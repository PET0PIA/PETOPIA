package com.ms.petopia.api.reservation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * reservation_pets에 INSERT할 예약 시점 반려동물 스냅샷 1건.
 *
 * <p>원본(pets)을 참조하지 않고 값을 그대로 복사한다 - 사용자가 나중에 반려동물 정보를
 * 수정하거나 삭제해도 예약 기록은 변하지 않아야 한다(정책 P5).
 *
 * <p>{@code petAgeSnapshot}은 채우지 않는다. 나이는 매년 값이 바뀌는 파생 정보라
 * 불변 값인 생년월일을 저장하고 필요할 때 계산한다(V44 주석·통계 도메인 전달 문서 참고).
 */
@Getter
@Setter
@Builder
public class ReservationPetSnapshotInsertRow {

    /** useGeneratedKeys로 채워진다. 알레르기 스냅샷을 이 id에 매달기 위해 필요하다. */
    private Long reservationPetId;
    private Long reservationId;
    private Long petId;
    private String petNameSnapshot;
    private String petSpeciesSnapshot;
    private String petBreedSnapshot;
    private LocalDate petBirthDateSnapshot;
    private Boolean petHasAllergySnapshot;
    /** 라벨을 콤마로 이은 문자열(예: 닭고기,꽃가루). 목록·상세 표시용. */
    private String petAllergySnapshot;
    private LocalDateTime createdAt;
}
