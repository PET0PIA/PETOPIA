package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.PaymentConfirmationReservationRow;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletedCommand;
import com.ms.petopia.api.reservation.dto.ReservationPaymentContextRow;
import com.ms.petopia.api.reservation.dto.ReservationPaymentReceiptRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReservationPaymentConfirmationMapper {

    ReservationPaymentContextRow selectPaymentContext(@Param("reservationId") Long reservationId);

    ReservationPaymentReceiptRow selectReceiptByEventId(@Param("eventId") String eventId);

    ReservationPaymentReceiptRow selectReceiptByReservationId(@Param("reservationId") Long reservationId);

    PaymentConfirmationReservationRow selectReservationForUpdate(@Param("reservationId") Long reservationId);

    int insertReceipt(
            @Param("command") ReservationPaymentCompletedCommand command,
            @Param("receivedAt") LocalDateTime receivedAt
    );

    int confirmPendingReservation(
            @Param("reservationId") Long reservationId,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int insertConfirmedHistory(
            @Param("reservationId") Long reservationId,
            @Param("paymentId") Long paymentId,
            @Param("paidAmount") long paidAmount,
            @Param("now") LocalDateTime now
    );
}
