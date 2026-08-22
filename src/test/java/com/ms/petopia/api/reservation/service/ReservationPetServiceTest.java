package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationPetAllergyInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationPetAllergyRow;
import com.ms.petopia.api.reservation.dto.ReservationPetOwnedAllergyRow;
import com.ms.petopia.api.reservation.dto.ReservationPetOwnedRow;
import com.ms.petopia.api.reservation.dto.ReservationPetResponse;
import com.ms.petopia.api.reservation.dto.ReservationPetRow;
import com.ms.petopia.api.reservation.dto.ReservationPetSnapshotInsertRow;
import com.ms.petopia.api.reservation.mapper.ReservationPetMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationPetServiceTest {

    private static final Long RESERVATION_ID = 500L;
    private static final Long USER_ID = 20L;
    private static final Long FAIR_ID = 10L;
    private static final Long PET_ID = 7L;
    private static final Long OTHER_PET_ID = 8L;
    private static final Long RESERVATION_PET_ID = 900L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private ReservationPetMapper reservationPetMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private ReservationPetService reservationPetService;

    // ===== 정책 P3: 0마리도 유효하다 =====

    @Test
    @DisplayName("동반 목록이 비어 있으면 아무것도 저장하지 않는다")
    void attachPets_목록이비면_저장하지않는다() {
        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, List.of());

        verify(reservationPetMapper, never()).selectOwnedPets(any(), anyList());
        verify(reservationPetMapper, never()).insertReservationPet(any());
    }

    @Test
    @DisplayName("동반 목록이 null이면 아무것도 저장하지 않는다")
    void attachPets_목록이null이면_저장하지않는다() {
        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, null);

        verify(reservationPetMapper, never()).insertReservationPet(any());
    }

    @Test
    @DisplayName("동반 금지 행사라도 목록이 비어 있으면 예약을 막지 않는다")
    void attachPets_동반금지인데목록이비면_통과한다() {
        reservationPetService.attachPets(RESERVATION_ID, USER_ID, false, List.of());

        verify(reservationPetMapper, never()).insertReservationPet(any());
    }

    // ===== 정책 P2: 동반 금지 행사는 서버가 거절한다 =====

    @Test
    @DisplayName("동반 금지 행사에 반려동물을 담으면 R024로 거절한다")
    void attachPets_동반금지행사면_거절한다() {
        assertThatThrownBy(() -> reservationPetService.attachPets(RESERVATION_ID, USER_ID, false, List.of(PET_ID)))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_PET_NOT_ALLOWED);
        verify(reservationPetMapper, never()).selectOwnedPets(any(), anyList());
    }

    // ===== 정책 P4: 소유권은 서버가 검증한다 =====

    @Test
    @DisplayName("남의 반려동물이거나 사라진 반려동물이면 R025로 거절한다")
    void attachPets_소유가아니면_거절한다() {
        //user_id 조건 때문에 남의 petId는 결과에서 빠진다 - 개수 차이로 걸러진다.
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID, OTHER_PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", null, null)));

        assertThatThrownBy(() -> reservationPetService.attachPets(
                RESERVATION_ID, USER_ID, true, List.of(PET_ID, OTHER_PET_ID)))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_PET_NOT_FOUND);
        verify(reservationPetMapper, never()).insertReservationPet(any());
    }

    @Test
    @DisplayName("petId가 null이거나 0 이하면 INVALID_INPUT_VALUE로 거절한다")
    void attachPets_petId가이상하면_거절한다() {
        assertThatThrownBy(() -> reservationPetService.attachPets(
                RESERVATION_ID, USER_ID, true, java.util.Arrays.asList(PET_ID, null)))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> reservationPetService.attachPets(
                RESERVATION_ID, USER_ID, true, List.of(0L)))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ===== 정책 P5: 스냅샷 =====

    @Test
    @DisplayName("예약 시점 정보를 스냅샷으로 복사해 저장한다")
    void attachPets_스냅샷으로복사한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", LocalDate.of(2023, 5, 1), true)));
        given(reservationPetMapper.selectOwnedPetAllergies(List.of(PET_ID)))
                .willReturn(List.of(
                        ownedAllergy(PET_ID, 100L, "닭고기", false, null),
                        ownedAllergy(PET_ID, 101L, "꽃가루", false, null)
                ));
        givenInsertAssignsReservationPetId();

        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, List.of(PET_ID));

        ArgumentCaptor<ReservationPetSnapshotInsertRow> captor =
                ArgumentCaptor.forClass(ReservationPetSnapshotInsertRow.class);
        verify(reservationPetMapper).insertReservationPet(captor.capture());
        ReservationPetSnapshotInsertRow saved = captor.getValue();
        assertThat(saved.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(saved.getPetId()).isEqualTo(PET_ID);
        assertThat(saved.getPetNameSnapshot()).isEqualTo("초코");
        assertThat(saved.getPetBirthDateSnapshot()).isEqualTo(LocalDate.of(2023, 5, 1));
        assertThat(saved.getPetHasAllergySnapshot()).isTrue();
        //통계 도메인에 전달한 계약: 라벨을 콤마로 이은 문자열
        assertThat(saved.getPetAllergySnapshot()).isEqualTo("닭고기,꽃가루");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("알레르기가 없으면 플랫 텍스트는 null이다")
    void attachPets_알레르기가없으면_플랫텍스트는null이다() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", null, false)));
        given(reservationPetMapper.selectOwnedPetAllergies(List.of(PET_ID))).willReturn(List.of());
        givenInsertAssignsReservationPetId();

        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, List.of(PET_ID));

        ArgumentCaptor<ReservationPetSnapshotInsertRow> captor =
                ArgumentCaptor.forClass(ReservationPetSnapshotInsertRow.class);
        verify(reservationPetMapper).insertReservationPet(captor.capture());
        assertThat(captor.getValue().getPetAllergySnapshot()).isNull();
        verify(reservationPetMapper, never()).insertReservationPetAllergies(anyList());
    }

    @Test
    @DisplayName("알레르기 스냅샷을 정규화 테이블에도 저장하고, 기타만 직접 입력값을 남긴다")
    void attachPets_알레르기를_정규화테이블에도_저장한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", null, true)));
        given(reservationPetMapper.selectOwnedPetAllergies(List.of(PET_ID)))
                .willReturn(List.of(
                        //requiresText=false인데 other_text에 값이 남아 있어도 스냅샷에는 담지 않는다.
                        ownedAllergy(PET_ID, 100L, "닭고기", false, "쓰이지않는값"),
                        ownedAllergy(PET_ID, 199L, "기타", true, "특정 사료")
                ));
        givenInsertAssignsReservationPetId();

        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, List.of(PET_ID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservationPetAllergyInsertRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationPetMapper).insertReservationPetAllergies(captor.capture());
        List<ReservationPetAllergyInsertRow> rows = captor.getValue();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getAllergyTypeId()).isEqualTo(100L);
        assertThat(rows.get(0).getOtherTextSnapshot()).isNull();
        assertThat(rows.get(1).getAllergyTypeId()).isEqualTo(199L);
        assertThat(rows.get(1).getOtherTextSnapshot()).isEqualTo("특정 사료");
    }

    @Test
    @DisplayName("같은 petId를 두 번 보내면 한 번만 저장한다")
    void attachPets_중복petId는_한번만저장한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", null, null)));
        given(reservationPetMapper.selectOwnedPetAllergies(List.of(PET_ID))).willReturn(List.of());
        givenInsertAssignsReservationPetId();

        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, List.of(PET_ID, PET_ID));

        verify(reservationPetMapper).insertReservationPet(any());
    }

    @Test
    @DisplayName("알레르기 라벨이 255자를 넘으면 콤마 경계에서 잘라 저장한다")
    void attachPets_플랫텍스트가_컬럼상한을_넘으면_자른다() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", null, true)));
        //라벨 50자 * 10개 = 콤마 포함 509자로 컬럼 상한을 넘긴다.
        String longLabel = "가".repeat(50);
        List<ReservationPetOwnedAllergyRow> many = new java.util.ArrayList<>();
        for (int index = 0; index < 10; index++) {
            many.add(ownedAllergy(PET_ID, 100L + index, longLabel, false, null));
        }
        given(reservationPetMapper.selectOwnedPetAllergies(List.of(PET_ID))).willReturn(many);
        givenInsertAssignsReservationPetId();

        reservationPetService.attachPets(RESERVATION_ID, USER_ID, true, List.of(PET_ID));

        ArgumentCaptor<ReservationPetSnapshotInsertRow> captor =
                ArgumentCaptor.forClass(ReservationPetSnapshotInsertRow.class);
        verify(reservationPetMapper).insertReservationPet(captor.capture());
        String snapshot = captor.getValue().getPetAllergySnapshot();
        assertThat(snapshot).hasSizeLessThanOrEqualTo(255);
        //잘린 라벨이 반토막으로 남지 않는다.
        assertThat(snapshot).doesNotEndWith(",");
        assertThat(snapshot.split(",")).allMatch(label -> label.length() == 50);
    }

    // ===== 방문일 변경에서의 교체 =====

    @Test
    @DisplayName("교체는 알레르기 스냅샷을 먼저 지우고 본체를 지운 뒤 다시 담는다")
    void replacePets_지운뒤_다시담는다() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetMapper.selectOwnedPets(USER_ID, List.of(PET_ID)))
                .willReturn(List.of(ownedPet(PET_ID, "초코", null, null)));
        given(reservationPetMapper.selectOwnedPetAllergies(List.of(PET_ID))).willReturn(List.of());
        givenInsertAssignsReservationPetId();

        reservationPetService.replacePets(RESERVATION_ID, USER_ID, true, List.of(PET_ID));

        InOrder order = inOrder(reservationPetMapper);
        //알레르기가 reservation_pets를 타고 지워지므로 순서가 뒤집히면 지울 대상을 못 찾는다.
        order.verify(reservationPetMapper).deleteAllergiesByReservationId(RESERVATION_ID);
        order.verify(reservationPetMapper).deleteByReservationId(RESERVATION_ID);
        order.verify(reservationPetMapper).insertReservationPet(any());
    }

    @Test
    @DisplayName("빈 목록으로 교체하면 동반이 해제된다")
    void replacePets_빈목록이면_동반을해제한다() {
        reservationPetService.replacePets(RESERVATION_ID, USER_ID, true, List.of());

        verify(reservationPetMapper).deleteAllergiesByReservationId(RESERVATION_ID);
        verify(reservationPetMapper).deleteByReservationId(RESERVATION_ID);
        verify(reservationPetMapper, never()).insertReservationPet(any());
    }

    // ===== 동반 허용 여부 조회 =====

    @Test
    @DisplayName("동반 허용 여부를 읽을 수 없으면 금지로 본다")
    void isFairPetAllowed_값이없으면_금지로본다() {
        given(reservationPetMapper.selectFairPetAllowed(FAIR_ID)).willReturn(null);

        assertThat(reservationPetService.isFairPetAllowed(FAIR_ID)).isFalse();
    }

    @Test
    @DisplayName("동반 허용 여부를 그대로 돌려준다")
    void isFairPetAllowed_값을그대로돌려준다() {
        given(reservationPetMapper.selectFairPetAllowed(FAIR_ID)).willReturn(true);

        assertThat(reservationPetService.isFairPetAllowed(FAIR_ID)).isTrue();
    }

    // ===== 상세 조회 =====

    @Test
    @DisplayName("예약 상세용 조회는 알레르기를 한 번에 가져와 반려동물별로 붙인다")
    void getReservationPets_알레르기를_붙여준다() {
        given(reservationPetMapper.selectByReservationId(RESERVATION_ID))
                .willReturn(List.of(petRow(RESERVATION_PET_ID, PET_ID, "초코")));
        given(reservationPetMapper.selectAllergiesByReservationPetIds(List.of(RESERVATION_PET_ID)))
                .willReturn(List.of(allergyRow(RESERVATION_PET_ID, 199L, "OTHER", "기타", true, "특정 사료")));

        List<ReservationPetResponse> pets = reservationPetService.getReservationPets(RESERVATION_ID);

        assertThat(pets).hasSize(1);
        assertThat(pets.get(0).name()).isEqualTo("초코");
        assertThat(pets.get(0).allergies()).hasSize(1);
        assertThat(pets.get(0).allergies().get(0).otherText()).isEqualTo("특정 사료");
    }

    @Test
    @DisplayName("동반 반려동물이 없으면 빈 목록이고 알레르기를 조회하지 않는다")
    void getReservationPets_없으면_빈목록이다() {
        given(reservationPetMapper.selectByReservationId(RESERVATION_ID)).willReturn(List.of());

        assertThat(reservationPetService.getReservationPets(RESERVATION_ID)).isEmpty();
        verify(reservationPetMapper, never()).selectAllergiesByReservationPetIds(anyList());
    }

    // ===== 테스트 보조 =====

    private void givenInsertAssignsReservationPetId() {
        willAnswer(invocation -> {
            ReservationPetSnapshotInsertRow row = invocation.getArgument(0);
            row.setReservationPetId(RESERVATION_PET_ID);
            return 1;
        }).given(reservationPetMapper).insertReservationPet(any(ReservationPetSnapshotInsertRow.class));
    }

    private ReservationPetOwnedRow ownedPet(Long petId, String name, LocalDate birthDate, Boolean hasAllergy) {
        ReservationPetOwnedRow row = new ReservationPetOwnedRow();
        row.setPetId(petId);
        row.setName(name);
        row.setSpecies("강아지");
        row.setBreed("포메라니안");
        row.setBirthDate(birthDate);
        row.setHasAllergy(hasAllergy);
        return row;
    }

    private ReservationPetOwnedAllergyRow ownedAllergy(
            Long petId, Long allergyTypeId, String label, boolean requiresText, String otherText
    ) {
        ReservationPetOwnedAllergyRow row = new ReservationPetOwnedAllergyRow();
        row.setPetId(petId);
        row.setAllergyTypeId(allergyTypeId);
        row.setLabel(label);
        row.setRequiresText(requiresText);
        row.setOtherText(otherText);
        return row;
    }

    private ReservationPetRow petRow(Long reservationPetId, Long petId, String name) {
        ReservationPetRow row = new ReservationPetRow();
        row.setReservationPetId(reservationPetId);
        row.setReservationId(RESERVATION_ID);
        row.setPetId(petId);
        row.setPetNameSnapshot(name);
        row.setPetSpeciesSnapshot("강아지");
        row.setPetBreedSnapshot("포메라니안");
        row.setPetBirthDateSnapshot(LocalDate.of(2023, 5, 1));
        row.setPetHasAllergySnapshot(true);
        return row;
    }

    private ReservationPetAllergyRow allergyRow(
            Long reservationPetId, Long allergyTypeId, String category, String label,
            boolean requiresText, String otherTextSnapshot
    ) {
        ReservationPetAllergyRow row = new ReservationPetAllergyRow();
        row.setReservationPetId(reservationPetId);
        row.setAllergyTypeId(allergyTypeId);
        row.setCode(label);
        row.setCategory(category);
        row.setLabel(label);
        row.setRequiresText(requiresText);
        row.setOtherTextSnapshot(otherTextSnapshot);
        return row;
    }
}
