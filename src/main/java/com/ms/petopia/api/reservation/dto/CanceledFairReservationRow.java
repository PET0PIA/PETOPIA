package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 취소된 행사에 묶여 있는, 아직 살아 있는 예약 한 건.
 * {@code ReservationFairCancelSyncService}가 "지금 취소로 넘겨도 되는지"를 판정하는 데 필요한
 * 값만 담는다. 결제·환불 값은 결제/환불 도메인 테이블에서 읽기만 한 참고값이다.
 */
@Getter
@Setter
public class CanceledFairReservationRow {

    private Long reservationId;
    private Long userId;
    private Long fairId;
    private LocalDate visitDate;
    /** ADVANCE만 정원을 점유하므로 반납 대상을 가린다. */
    private String reservationType;
    /** 조회 시점 예약 상태(PENDING_PAYMENT / CONFIRMED). 상태 CAS의 기대값으로 쓴다. */
    private String reservationStatus;
    /** 예약금 결제의 상태. 결제 행이 없는 무료 예약이면 null. */
    private String paymentStatus;
    /** 예약금 결제에 딸린 환불의 상태(REQUESTED / COMPLETED / REJECTED). 환불이 없으면 null. */
    private String refundStatus;
    /** 이력에 남길 환불 PK. 환불이 없으면 null. */
    private Long refundId;
    /** 이력에 남길 환불 금액. 환불이 없으면 null. */
    private Long refundAmount;
}
