package com.ms.petopia.api.fair.dto;

/**
 * fairs.status. 신청부터 종료까지의 생애주기 전체를 하나의 상태값으로 표현한다.
 *
 * <pre>
 * RECEIVED -> REJECTED       (심사 반려, FairService.review)
 * RECEIVED -> PAYMENT_PENDING (심사 승인, FairService.review)
 * PAYMENT_PENDING -> EXPIRED  (개설비 결제 기한 초과, FairTransitionService.expireDuePayments)
 * PAYMENT_PENDING -> PREPARING (개설비 결제 완료, FairTransitionService.completeDuePayments)
 * PREPARING -> IN_PROGRESS    (운영 시작일 도래, FairTransitionService.startDueFairs)
 * IN_PROGRESS -> ENDED        (운영 종료일 경과, FairTransitionService.endDueFairs)
 * </pre>
 *
 * <p>공개(published_at) 여부는 상태값과 별개다 - PAYMENT_PENDING 이상이면 관리자가 언제든
 * 수동으로 공개할 수 있다({@code FairService.publish}).
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
