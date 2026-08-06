package com.ms.petopia.api.refund.dto;

/** 환불 5경로. REFUND.refund_reason 컬럼 값과 1:1 대응([[project_petopia_payment_domain]] 환불 5경로 참고). */
public enum RefundReason {
    /** 관람객 자진 예약취소 */
    USER_CANCEL,
    /** 행사취소로 인한 관람객예약 일괄취소 */
    FAIR_CANCEL_USER,
    /** 참가업체 자진취소 */
    VENDOR_CANCEL,
    /** 행사취소로 인한 참가업체 환불 */
    FAIR_CANCEL_VENDOR,
    /** 행사 개설비 환불(관리자 수동 처리) */
    OPENING_FEE_MANUAL
}
