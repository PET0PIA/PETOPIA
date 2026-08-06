package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/**
 * 부스 QR 스캔 결과. 관람객 개인정보는 담지 않는다(참가업체 리더기 노출 금지 규칙).
 * firstVisit=false면 이미 방문한 예약이므로 샘플·상품 중복 수령 안내에 쓴다.
 */
public record BoothScanResponse(
        String resultCode,
        boolean firstVisit,
        int visitCount,
        LocalDateTime firstVisitedAt
) {
}
