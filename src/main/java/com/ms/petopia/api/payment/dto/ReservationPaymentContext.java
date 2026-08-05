package com.ms.petopia.api.payment.dto;


import java.time.LocalDateTime;

/** 예약 도메인의 결제 컨텍스트 조회(GET .../payment-context) 응답을 그대로 매핑. */
public record ReservationPaymentContext(Long reservationId,
                                        Long fairId,
                                        Long payerUserId,
                                        String reservationType,
                                        long amount,
                                        LocalDateTime PaymentExpiresAt) {

}
