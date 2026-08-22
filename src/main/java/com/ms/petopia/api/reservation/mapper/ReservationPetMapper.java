package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ReservationPetAllergyInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationPetAllergyRow;
import com.ms.petopia.api.reservation.dto.ReservationPetOwnedRow;
import com.ms.petopia.api.reservation.dto.ReservationPetOwnedAllergyRow;
import com.ms.petopia.api.reservation.dto.ReservationPetRow;
import com.ms.petopia.api.reservation.dto.ReservationPetSnapshotInsertRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 예약에 담긴 반려동물 스냅샷(reservation_pets / reservation_pet_allergies) 매퍼.
 * XML은 {@code mapper/reservation/ReservationPetMapper.xml}에 있다.
 *
 * <p>pets·pet_allergies는 <b>조회만</b> 한다. 반려동물 원본은 pet 도메인 소유다.
 */
@Mapper
public interface ReservationPetMapper {

    /** 행사가 반려동물 동반을 허용하는지. 방문일 변경처럼 행사 컨텍스트를 따로 안 읽는 경로에서 쓴다. */
    Boolean selectFairPetAllowed(@Param("fairId") Long fairId);

    /**
     * 예약자 본인이 소유한 반려동물만 골라 온다. petId만으로 조회하지 않고 user_id를 함께
     * 조건에 걸어, 남의 petId를 API로 직접 밀어 넣어도 결과에서 빠지게 한다(정책 P4).
     * 요청한 개수와 결과 개수가 다르면 서비스가 거절한다.
     */
    List<ReservationPetOwnedRow> selectOwnedPets(
            @Param("userId") Long userId,
            @Param("petIds") List<Long> petIds
    );

    /** 위에서 고른 반려동물들의 알레르기를 한 번에(N+1 방지). 마스터 라벨까지 조인해 온다. */
    List<ReservationPetOwnedAllergyRow> selectOwnedPetAllergies(@Param("petIds") List<Long> petIds);

    /** 스냅샷 1건 저장. reservationPetId가 useGeneratedKeys로 채워진다. */
    int insertReservationPet(ReservationPetSnapshotInsertRow row);

    /** 알레르기 스냅샷을 한 번에 저장(다중행 INSERT). rows가 비어있으면 호출하지 않는다. */
    int insertReservationPetAllergies(@Param("rows") List<ReservationPetAllergyInsertRow> rows);

    /** 예약의 반려동물 스냅샷 조회(상세 화면용). */
    List<ReservationPetRow> selectByReservationId(@Param("reservationId") Long reservationId);

    /** 위 스냅샷들의 알레르기를 한 번에. reservationPetIds가 비어있으면 호출하지 않는다. */
    List<ReservationPetAllergyRow> selectAllergiesByReservationPetIds(
            @Param("reservationPetIds") List<Long> reservationPetIds
    );

    /** 방문일 변경에서 목록을 교체할 때 기존 스냅샷을 먼저 지운다. 알레르기 스냅샷이 먼저다. */
    int deleteAllergiesByReservationId(@Param("reservationId") Long reservationId);

    int deleteByReservationId(@Param("reservationId") Long reservationId);
}
