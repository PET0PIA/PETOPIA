package com.ms.petopia.api.payment.controller;

/**
 * 다른 도메인이 서버-투-서버로 결제 취소·만료 API를 호출할 때 쓰는 임시 내부 헤더.
 * 예약 도메인의 {@code TemporaryAuthHeaders} 패턴과 동일 — 결제 도메인은
 * 예약·행사·참가업체 3곳 모두한테 호출받아야 해서 CALLER 값을 3개로 열어둔다.
 *
 * <p>TODO 인증 도메인 완성 후: 헤더 문자열 검증 대신 내부 서비스 인증/서명으로 교체.
 */
public final class PaymentInternalAuthHeaders {

    public static final String INTERNAL_CALLER = "X-Internal-Caller";
    public static final String RESERVATION_CALLER = "RESERVATION";
    public static final String FAIR_CALLER = "FAIR";
    public static final String VENDOR_APPLICATION_CALLER = "VENDOR_APPLICATION";

    private PaymentInternalAuthHeaders() {
    }
}
