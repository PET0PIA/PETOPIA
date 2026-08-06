package com.ms.petopia.api.refund.controller;

/**
 * 회원·인증 도메인 연동 전까지 사용하는 임시 헤더.
 * TODO 인증 도메인 구현 후 X-User-Id를 인증 Principal의 userId로 교체한다.
 */
public final class RefundTemporaryAuthHeaders {
    public static final String USER_ID = "X-User-Id";
    private RefundTemporaryAuthHeaders() {
    }
}
