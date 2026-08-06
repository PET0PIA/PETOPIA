package com.ms.petopia.api.settlement.controller;

/**
 * 회원·인증 도메인 연동 전까지 사용하는 임시 헤더.
 * TODO 인증 도메인 구현 후 X-User-Id를 인증 Principal의 userId로,
 *      정산 확정은 SUPER_ADMIN role 검증으로 교체한다.
 */
public final class SettlementTemporaryAuthHeaders {
    public static final String USER_ID = "X-User-Id";
    private SettlementTemporaryAuthHeaders() {
    }
}
