package com.ms.petopia.api.booth.controller;

/**
 * 회원·인증 도메인 연동 전까지 Postman/프론트 테스트에 사용하는 임시 헤더다.
 * TODO 인증 도메인 구현 후 X-User-Id를 인증 Principal의 userId로 교체한다.
 *
 * <p>다른 도메인에도 내용이 같은 {@code TemporaryAuthHeaders}가 있다. MyBatis
 * type-aliases-package가 클래스 simple name으로 alias를 등록해서 이름이 겹치면
 * 충돌(TypeException)이 나기 때문에 도메인 접두사를 붙여 분리한다.
 */
public class BoothTemporaryAuthHeaders {

    public static final String USER_ID = "X-User-Id";

    private BoothTemporaryAuthHeaders() {
    }

}