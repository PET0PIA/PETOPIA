package com.ms.petopia.api.fair.controller;

/**
 * 회원·인증 도메인 연동 전까지 Postman/프론트 테스트에 사용하는 임시 헤더다.
 * TODO 인증 도메인 구현 후 X-User-Id를 인증 Principal의 userId로 교체한다.
 *
 * <p>reservation 도메인의 동일 클래스와 내용이 같다. 도메인 간 결합을 피하기 위해
 * 도메인마다 독립적으로 둔다(추후 인증 도메인 완성 시 양쪽 다 제거될 임시 코드다).
 */
public final class TemporaryAuthHeaders {

    public static final String USER_ID = "X-User-Id";

    private TemporaryAuthHeaders() {
    }
}
