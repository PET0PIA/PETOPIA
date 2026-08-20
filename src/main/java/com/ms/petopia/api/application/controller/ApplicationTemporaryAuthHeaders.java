package com.ms.petopia.api.application.controller;

/**
 * 도메인 간 내부 호출 전용 헤더. fair/reservation 도메인에도 내용이 같은 클래스가 각각 있다 -
 * MyBatis type-aliases-package가 클래스 simple name으로 alias를 등록해서 같은 이름을 쓰면
 * alias 충돌(TypeException)이 나기 때문에 도메인마다 독립적으로 둔다.
 */
public final class ApplicationTemporaryAuthHeaders {

    public static final String INTERNAL_CALLER = "X-Internal-Caller";
    public static final String PAYMENT_CALLER = "PAYMENT";

    private ApplicationTemporaryAuthHeaders() {
    }

}
