package com.ms.petopia.api.refund.dto;

/** 환불을 촉발한 도메인. REFUND.requested_by_domain 컬럼 값과 1:1 대응(감사·추적용). */
public enum RequestedByDomain {
    RESERVATION,
    FAIR,
    VENDOR,
    /** 행사 개설비 등 결제 도메인 관리자가 직접 처리하는 환불(OPENING_FEE_MANUAL과 주로 짝을 이룸). */
    PAYMENT_ADMIN
}
