package com.ms.petopia.api.payment.dto;

/**
 * 참가비 결제 생성 요청.
 *
 * <p>결제 도메인이 application 테이블을 직접 조회하지 않기로 했기 때문에(애그리거트 간
 * ID 참조 원칙 유지), 결제에 필요한 금액과 소속 정보를 호출자가 직접 실어보낸다.
 * 서버는 이 값을 그대로 신뢰하고 결제 레코드를 만든다 — 값 검증은 하지 않는다(MVP 트레이드오프).
 */
public record VendorFeePaymentRequest(Long fairId, Long businessId, Long amount) {

}
