package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 스캔하는 VENDOR가 소유한 부스 정보. 소유가 아니면 조회되지 않는다(null).
 * fairId는 부스가 참가한 행사로, QR의 행사와 일치하는지 검증에 쓴다.
 */
@Getter
@Setter
public class BoothScanContext {
    private Long boothId;
    private Long businessId;
    private Long fairId;
}
