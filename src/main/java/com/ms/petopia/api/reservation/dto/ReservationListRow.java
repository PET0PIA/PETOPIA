package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationListRow {
    private Long reservationId;
    private String reservationNo;
    private Long fairId;
    private String fairName;
    private String fairPosterImageUrl;
    private LocalDate visitDate;
    private LocalTime entryStartTime;
    private LocalTime entryEndTime;
    private String reservationStatus;
    private String reservationType;
    private long amount;
    private LocalDateTime reservedAt;
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime checkedInAt;
    // 취소된 예약이 "사용자 자진취소"인지 "행사 취소로 인한 자동 취소"인지 가리는 값.
    // reservations를 CANCELED로 바꾸는 경로는 자진취소(항상 user_id가 들어감)와
    // 행사 취소 정리(ReservationFairCancelSyncService, 항상 NULL) 둘뿐이라 이 값으로 구분된다.
    private Long canceledBy;
    // ── 아래 2개는 예약 상세 전용(selectReservationForOwner만 채운다. 목록 조회에서는 null). ──
    // 행사가 정한 취소·변경 가능 기한(입장 몇 시간 전까지). 행사가 정하지 않았으면 null이고,
    // 그때는 ReservationDeadlinePolicy의 기본값을 쓴다.
    private Integer cancelDeadlineHours;
    private Integer changeDeadlineHours;
    // ── 아래 4개는 예약 상세 화면의 "결제 ID / 결제수단"용. 무료 예약은 결제 행이 없어 전부 null. ──
    // paymentId를 long이 아니라 Long으로 두는 이유: 결제 행이 없을 때 null이 그대로 들어와야 한다.
    private Long paymentId;
    private String paymentStatus;
    private String paymentMethod;
    private String easyPayProvider;
    /**
     * 예약금 환불 상태(REQUESTED/COMPLETED/REJECTED). 환불이 없으면 null.
     *
     * <p>목록 화면이 "취소됨"만으로는 구분할 수 없는 세 상황(환불받은 취소 / 결제 전 취소 /
     * 무료 예약 취소)을 가르는 데 쓴다. 금액은 목록에 다시 쓰지 않기로 해서 환불 금액은
     * 싣지 않는다 - 필요해지면 그때 추가한다. 목록 조회(selectMyReservations)에서만 채워지고
     * 상세 조회에서는 null이다(상세는 결제 상세 API로 환불 정보를 더 자세히 가져온다).
     */
    private String refundStatus;
}
