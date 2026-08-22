package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 예약 스냅샷의 알레르기 1건(조회용). 여러 reservation_pet_id를 한 번에 가져와
 * 서비스에서 그룹핑한다(N+1 방지).
 *
 * <p>마스터를 조인해 라벨을 가져오되 {@code is_active}로 걸러내지 않는다 - 나중에
 * 비활성화된 항목이라도 과거 예약 기록에는 그대로 남아 있어야 한다.
 */
@Getter
@Setter
public class ReservationPetAllergyRow {

    private Long reservationPetId;
    private Long allergyTypeId;
    private String code;
    private String category;
    private String label;
    private boolean requiresText;
    private String otherTextSnapshot;
}
