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
    // ── 아래 4개는 예약 상세 화면의 "결제 ID / 결제수단"용. 무료 예약은 결제 행이 없어 전부 null. ──
    // paymentId를 long이 아니라 Long으로 두는 이유: 결제 행이 없을 때 null이 그대로 들어와야 한다.
    private Long paymentId;
    private String paymentStatus;
    private String paymentMethod;
    private String easyPayProvider;
}
