package com.ms.petopia.api.reservation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * reservations INSERT 파라미터와 생성된 PK를 담는 MyBatis용 객체.
 */
@Getter
@Setter
@Builder
public class ReservationInsertRow {

    private Long reservationId;
    private String reservationNo;
    private Long fairId;
    private Long userId;
    private LocalDate visitDate;
    private String reservationType;
    private String status;
    private long reservationAmount;
    private String reserverName;
    private String reserverPhone;
    private String reserverEmail;
    private String channel;
    private boolean agreedTerms;
    private String reservationTermsVersion;
    private LocalDateTime reservationTermsAgreedAt;
    private LocalDateTime reservedAt;
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
