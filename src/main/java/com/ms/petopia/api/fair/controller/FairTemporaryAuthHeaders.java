package com.ms.petopia.api.fair.controller;

/**
 * 사용자 신원(X-User-Id)은 이제 실제 JWT({@code @AuthenticationPrincipal})로 받는다
 * ({@code SecurityConfig}의 "Fair 도메인" 섹션 참고). 이 클래스에는 도메인 간 내부 호출 전용
 * 헤더만 남아있다({@code FairPaymentContractController} - 결제 도메인이 서비스 대 서비스로
 * 호출하는 계약 API라 사용자 JWT 대상이 아니다).
 *
 * <p>reservation 도메인에도 내용이 같은 {@code TemporaryAuthHeaders}가 있다. 도메인 간 결합을
 * 피하려고 도메인마다 독립적으로 두되, MyBatis {@code type-aliases-package}가
 * {@code com.ms.petopia} 전체를 스캔하며 클래스 simple name으로 alias를 등록하기 때문에
 * 같은 이름을 쓰면 alias 충돌(TypeException)이 난다. 그래서 도메인 접두사를 붙여 이름을 분리한다.
 */
public final class FairTemporaryAuthHeaders {

    /**
     * 내부(도메인 간) API 호출자 식별용. reservation 도메인의 {@code TemporaryAuthHeaders}와
     * 이름/값이 같지만, 이 클래스 상단 주석과 같은 이유로 도메인마다 독립적으로 둔다.
     */
    public static final String INTERNAL_CALLER = "X-Internal-Caller";
    public static final String PAYMENT_CALLER = "PAYMENT";

    private FairTemporaryAuthHeaders() {
    }
}
