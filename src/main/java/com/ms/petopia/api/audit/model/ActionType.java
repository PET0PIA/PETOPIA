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
    COMMISSION_RATE_UPDATE

}
