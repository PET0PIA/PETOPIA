package com.ms.petopia.api.commisionrate.dto;


/** COMMISSION_RATE.scope 컬럼 값과 1:1 대응. 전역 기본값 + 행사별 override 2단계 구조. */
public enum CommissionRateScope {

    /** 전역 기본값 - fairId 없이 적용 */
    GLOBAL,
    /** 특정 행사 override - fairId 필수, GLOBAL보다 우선 적용 */
    FAIR
}
