package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationPetAllergyInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationPetAllergyRow;
import com.ms.petopia.api.reservation.dto.ReservationPetOwnedAllergyRow;
import com.ms.petopia.api.reservation.dto.ReservationPetOwnedRow;
import com.ms.petopia.api.reservation.dto.ReservationPetResponse;
import com.ms.petopia.api.reservation.dto.ReservationPetResponse.ReservationPetAllergyResponse;
import com.ms.petopia.api.reservation.dto.ReservationPetRow;
import com.ms.petopia.api.reservation.dto.ReservationPetSnapshotInsertRow;
import com.ms.petopia.api.reservation.mapper.ReservationPetMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 예약에 동반 반려동물을 담고 스냅샷으로 보관하는 공통 처리.
 *
 * <p>사전예약 생성·현장 직접예매 생성·방문일 변경 세 경로가 같은 규칙을 써야 해서 한곳에 뒀다.
 * 규칙이 갈라지면 "화면에서만 막히고 API로는 통과하는" 구멍이 생긴다.
 *
 * <p>확정 정책(docs/pet-companion-reservation-plan.md §2-3):
 * <ul>
 *   <li>P2 — 동반 금지 행사의 예약에는 반려동물을 담을 수 없다. 서버가 거절한다.</li>
 *   <li>P3 — 동반 가능 행사에서 0마리 이상 담을 수 있다. 등록된 반려동물이 없어도 예약 자체는 된다.</li>
 *   <li>P4 — 예약자 본인이 등록한 반려동물만 담을 수 있다. 서버가 소유권을 검증한다.</li>
 *   <li>P5 — 예약 시점 정보를 스냅샷으로 보관한다. 원본이 수정·삭제돼도 예약 기록은 그대로다.</li>
 *   <li>P6 — 반려동물은 인원이 아니다. 예약 매수·정원·QR 수에 영향을 주지 않는다.</li>
 * </ul>
 *
 * <p>트랜잭션은 호출하는 쪽(예약 생성·변경)의 것에 참여한다. 예약이 롤백되면 스냅샷도 함께
 * 롤백되어야 하므로 이 클래스에 별도 트랜잭션 경계를 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ReservationPetService {

    /**
     * pet_allergy_snapshot VARCHAR(255)를 넘기지 않기 위한 상한. 정규화 테이블
     * (reservation_pet_allergies)에 전체가 남으므로 표시용 문자열만 잘라낸다.
     */
    private static final int ALLERGY_SNAPSHOT_MAX_LENGTH = 255;

    private final ReservationPetMapper reservationPetMapper;
    private final ReservationTimeProvider timeProvider;

    /**
     * 예약에 동반 반려동물을 담는다. petIds가 비어 있으면 아무것도 하지 않는다(P3).
     *
     * @param fairPetAllowed 행사의 동반 허용 여부. 호출부가 이미 읽어온 값을 넘긴다
     *                       (생성 경로는 행사 컨텍스트 조회에 이미 포함되어 있다).
     */
    public void attachPets(Long reservationId, Long userId, boolean fairPetAllowed, List<Long> petIds) {
        List<Long> distinctPetIds = distinct(petIds);
        if (distinctPetIds.isEmpty()) {
            return;
        }
        if (!fairPetAllowed) {
            throw new CommonException(ErrorCode.RESERVATION_PET_NOT_ALLOWED);
        }

        //소유권 검증 + "예약 도중 삭제됐는지" 확인을 한 쿼리로 겸한다. user_id를 조건에 걸었으므로
        //남의 petId나 이미 지워진 petId는 결과에서 빠지고, 개수 비교로 걸러진다.
        List<ReservationPetOwnedRow> ownedPets = reservationPetMapper.selectOwnedPets(userId, distinctPetIds);
        if (ownedPets.size() != distinctPetIds.size()) {
            throw new CommonException(ErrorCode.RESERVATION_PET_NOT_FOUND);
        }

        Map<Long, List<ReservationPetOwnedAllergyRow>> allergiesByPetId =
                reservationPetMapper.selectOwnedPetAllergies(distinctPetIds).stream()
                        .collect(Collectors.groupingBy(
                                ReservationPetOwnedAllergyRow::getPetId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Map<Long, ReservationPetOwnedRow> petsById = ownedPets.stream()
                .collect(Collectors.toMap(ReservationPetOwnedRow::getPetId, pet -> pet, (a, b) -> a, LinkedHashMap::new));

        LocalDateTime now = timeProvider.now();
        List<ReservationPetAllergyInsertRow> allergyRows = new ArrayList<>();
        //요청 순서를 유지해 저장한다 - 사용자가 화면에서 고른 순서가 상세에서도 같아야 한다.
        for (Long petId : distinctPetIds) {
            ReservationPetOwnedRow pet = petsById.get(petId);
            List<ReservationPetOwnedAllergyRow> allergies = allergiesByPetId.getOrDefault(petId, List.of());

            ReservationPetSnapshotInsertRow row = ReservationPetSnapshotInsertRow.builder()
                    .reservationId(reservationId)
                    .petId(petId)
                    .petNameSnapshot(pet.getName())
                    .petSpeciesSnapshot(pet.getSpecies())
                    .petBreedSnapshot(pet.getBreed())
                    .petBirthDateSnapshot(pet.getBirthDate())
                    .petHasAllergySnapshot(pet.getHasAllergy())
                    .petAllergySnapshot(toFlatAllergyText(allergies))
                    .createdAt(now)
                    .build();
            reservationPetMapper.insertReservationPet(row);

            for (ReservationPetOwnedAllergyRow allergy : allergies) {
                allergyRows.add(new ReservationPetAllergyInsertRow(
                        row.getReservationPetId(),
                        allergy.getAllergyTypeId(),
                        allergy.isRequiresText() ? allergy.getOtherText() : null
                ));
            }
        }
        if (!allergyRows.isEmpty()) {
            reservationPetMapper.insertReservationPetAllergies(allergyRows);
        }
    }

    /**
     * 동반 반려동물 목록을 교체한다(방문일 변경에서 사용). 기존 스냅샷을 지우고 새로 담는다.
     *
     * <p>부분 갱신을 지원하지 않는 이유는 pet 도메인의 알레르기 수정과 같다 - "지금 화면에
     * 보이는 목록이 곧 저장될 목록"이 사용자에게 가장 예측 가능하다. 스냅샷을 다시 뜨는 것이라
     * 그 사이 원본이 바뀌었으면 <b>변경 시점의 값</b>으로 갱신된다.
     */
    public void replacePets(Long reservationId, Long userId, boolean fairPetAllowed, List<Long> petIds) {
        //비어 있는 목록으로의 교체(동반 취소)도 유효한 요청이라, 여기서는 조기 반환하지 않는다.
        //금지 행사에 목록을 담아 보내는 경우는 attachPets가 거절한다.
        reservationPetMapper.deleteAllergiesByReservationId(reservationId);
        reservationPetMapper.deleteByReservationId(reservationId);
        attachPets(reservationId, userId, fairPetAllowed, petIds);
    }

    /** 행사가 동반을 허용하는지. 행사 컨텍스트를 따로 읽지 않는 경로(방문일 변경)용. */
    public boolean isFairPetAllowed(Long fairId) {
        //행사 자체가 없으면 여기까지 오지 않는다(호출부가 예약·행사를 이미 확인했다).
        //그래도 값이 없으면 "허용"으로 보지 않고 금지로 본다 - 모르는 상태에서 담는 쪽이 위험하다.
        return Boolean.TRUE.equals(reservationPetMapper.selectFairPetAllowed(fairId));
    }

    /** 예약 상세 화면용. 동반 반려동물이 없으면 빈 목록이다. */
    public List<ReservationPetResponse> getReservationPets(Long reservationId) {
        List<ReservationPetRow> pets = reservationPetMapper.selectByReservationId(reservationId);
        if (pets.isEmpty()) {
            return List.of();
        }
        List<Long> reservationPetIds = pets.stream().map(ReservationPetRow::getReservationPetId).toList();
        Map<Long, List<ReservationPetAllergyResponse>> allergiesByReservationPetId = new LinkedHashMap<>();
        for (ReservationPetAllergyRow row : reservationPetMapper.selectAllergiesByReservationPetIds(reservationPetIds)) {
            allergiesByReservationPetId
                    .computeIfAbsent(row.getReservationPetId(), key -> new ArrayList<>())
                    .add(ReservationPetAllergyResponse.from(row));
        }
        return pets.stream()
                .map(pet -> ReservationPetResponse.from(
                        pet,
                        allergiesByReservationPetId.getOrDefault(pet.getReservationPetId(), List.of())
                ))
                .toList();
    }

    /**
     * 통계·목록 표시용 플랫 텍스트. 라벨을 콤마로 이은다(예: {@code 닭고기,꽃가루}).
     * 통계 도메인에 전달한 계약이라 형식을 바꾸면 그쪽 조회가 깨진다
     * (docs/visit-stats-pet-allergy-plan.md §3 경로 B).
     */
    private String toFlatAllergyText(List<ReservationPetOwnedAllergyRow> allergies) {
        if (allergies.isEmpty()) {
            return null;
        }
        String joined = allergies.stream()
                .map(ReservationPetOwnedAllergyRow::getLabel)
                .collect(Collectors.joining(","));
        //컬럼 상한을 넘기면 INSERT가 실패한다. 전체 내용은 정규화 테이블에 남으므로
        //표시용 문자열만 자른다. 잘린 라벨이 반토막으로 보이지 않게 콤마 경계에서 끊는다.
        if (joined.length() <= ALLERGY_SNAPSHOT_MAX_LENGTH) {
            return joined;
        }
        String truncated = joined.substring(0, ALLERGY_SNAPSHOT_MAX_LENGTH);
        int lastComma = truncated.lastIndexOf(',');
        return lastComma > 0 ? truncated.substring(0, lastComma) : truncated;
    }

    private List<Long> distinct(List<Long> petIds) {
        if (petIds == null || petIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long petId : petIds) {
            //null이나 이상한 값이 섞여 오면 조회 조건이 흐트러진다 - 여기서 바로 거절한다.
            if (petId == null || petId <= 0) {
                throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
            }
            distinct.add(petId);
        }
        return List.copyOf(distinct);
    }
}
