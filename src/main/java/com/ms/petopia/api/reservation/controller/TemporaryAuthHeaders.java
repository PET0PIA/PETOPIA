package com.ms.petopia.api.reservation.controller;

/**
 * 회원·인증 도메인 연동 전까지 Postman/프론트 테스트에 사용하는 임시 헤더다.
 * TODO 인증 도메인 구현 후 X-User-Id를 인증 Principal의 userId로 교체한다.
 */
public final class TemporaryAuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String INTERNAL_CALLER = "X-Internal-Caller";
    public static final String PAYMENT_CALLER = "PAYMENT";

    private TemporaryAuthHeaders() {
    }
}
