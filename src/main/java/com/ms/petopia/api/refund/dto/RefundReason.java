package com.ms.petopia.api.refund.dto;

/** 환불 경로. REFUND.refund_reason 컬럼 값과 1:1 대응([[project_petopia_payment_domain]] 환불 5경로 참고). */
public enum RefundReason {
    /** 관람객 자진 예약취소 */
    USER_CANCEL,
    /** 행사취소로 인한 관람객예약 일괄취소 */
    FAIR_CANCEL_USER,
    /** 참가업체 자진취소 */
    VENDOR_CANCEL,
    /** 행사취소로 인한 참가업체 환불 */
    FAIR_CANCEL_VENDOR,
    /** 행사취소로 인한 개설비 환불(2026-08-23 자동환불 대상에 추가 - FairCancelRefundOrchestrationService 참고) */
    FAIR_CANCEL_OPENING_FEE,
    /** 행사 개설비 환불(관리자 수동 처리) - 취소가 아닌 사유로 개설비를 환불해야 할 때 쓴다.
     * 취소로 인한 개설비 환불은 이제 FAIR_CANCEL_OPENING_FEE로 자동 처리된다. */
    OPENING_FEE_MANUAL,
    /**
     * 관리자 대행 예약취소. 관람객 자진취소(USER_CANCEL)와 돈 흐름은 같지만, 취소를 누른
     * 주체가 관리자라서 환불 원장에서도 구분된다(민원 처리 건 집계·정산 대사용).
     */
    ADMIN_CANCEL
}
