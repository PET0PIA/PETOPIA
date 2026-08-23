package com.ms.petopia.api.audit.model;

public enum ActionType {
    // 보안
    LOGIN_FAIL,
    ROLE_CHANGE,
    ACCOUNT_DEACTIVATE,

    // 핵심 비즈니스 결정
    FAIR_APPROVE,
    FAIR_REJECT,
    FAIR_CANCEL_APPROVE,
    FAIR_EXPIRE,
    FAIR_START,
    FAIR_END,

    //재무
    PAYMENT_COMPLETION_RECEIVED,
    SETTLEMENT_CONFIRM,
    SETTLEMENT_REOPEN,
    COMMISSION_RATE_UPDATE,
    // 정산 재계산 시 계산 당시 스냅샷 요율과 지금 다시 조회한 유효 요율이 다를 때만 남긴다
    // (2026-08-22, FairSettlementService.recalculate 전용 - "수수료율 변경이력" 조회용).
    SETTLEMENT_RATE_CHANGED,

    // 콘텐츠 관리
    REVIEW_DELETE,

    // 운영 대행 - 관리자가 관람객 대신 처리한 건
    RESERVATION_ADMIN_CANCEL

}
