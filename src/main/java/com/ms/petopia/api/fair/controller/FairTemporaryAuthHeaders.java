package com.ms.petopia.api.fair.controller;

/**
 * 회원·인증 도메인 연동 전까지 Postman/프론트 테스트에 사용하는 임시 헤더다.
 * TODO 인증 도메인 구현 후 X-User-Id를 인증 Principal의 userId로 교체한다.
 *
 * <p>reservation 도메인에도 내용이 같은 {@code TemporaryAuthHeaders}가 있다. 도메인 간 결합을
 * 피하려고 도메인마다 독립적으로 두되, MyBatis {@code type-aliases-package}가
 * {@code com.ms.petopia} 전체를 스캔하며 클래스 simple name으로 alias를 등록하기 때문에
 * 같은 이름을 쓰면 alias 충돌(TypeException)이 난다. 그래서 도메인 접두사를 붙여 이름을 분리한다
 * (추후 인증 도메인 완성 시 양쪽 다 제거될 임시 코드다).
 */
public final class FairTemporaryAuthHeaders {

    public static final String USER_ID = "X-User-Id";

    private FairTemporaryAuthHeaders() {
    }
}
