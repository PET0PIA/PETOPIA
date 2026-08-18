package com.ms.petopia.api.reservation.controller;

/**
 * 예약 결제 내부 계약에서 사용하는 임시 헤더 상수.
 *
 * <p>외부 예약 API의 사용자 식별은 JWT 인증 Principal을 사용한다. {@code USER_ID}는 현재 알림
 * 컨트롤러와의 하위 호환을 위해서만 남아 있으며, 예약 컨트롤러에서는 사용하지 않는다.
 */
public final class TemporaryAuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String INTERNAL_CALLER = "X-Internal-Caller";
    public static final String PAYMENT_CALLER = "PAYMENT";

    private TemporaryAuthHeaders() {
    }
}
