package com.ms.petopia.api.fair.dto;

/**
 * fairs.status. 신청부터 종료까지의 생애주기 전체를 하나의 상태값으로 표현한다.
 *
 * <pre>
 * RECEIVED -> REJECTED
 * RECEIVED -> EXPIRED (개설비 미결제 기한 초과)
 * RECEIVED -> PAYMENT_PENDING -> PREPARING -> IN_PROGRESS -> ENDED
 * </pre>
 *
 * <p>취소는 상태값이 아니라 {@code fairs.canceled_at} 플래그로 관리한다.
 */
public enum FairStatus {

    /** 신청 접수, 심사 대기 */
    RECEIVED,

    /** 심사 반려 */
    REJECTED,

    /** 개설비 미결제 기한 초과 */
    EXPIRED,

    /** 심사 승인, 개설비 결제 대기 */
    PAYMENT_PENDING,

    /** 결제 완료, 운영 준비중 */
    PREPARING,

    /** 행사 진행중 */
    IN_PROGRESS,

    /** 행사 종료 */
    ENDED
}
