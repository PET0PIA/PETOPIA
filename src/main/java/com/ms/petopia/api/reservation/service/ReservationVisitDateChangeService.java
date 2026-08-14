package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.ReservationChangeFairDateRow;
import com.ms.petopia.api.reservation.dto.ReservationChangeReservationRow;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationChangeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationVisitDateChangeService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String ADVANCE = "ADVANCE";
    private static final long CHANGE_DEADLINE_HOURS = 12;

    private final ReservationChangeMapper changeMapper;
    private final ReservationCapacityMapper capacityMapper;
    private final EntryMapper entryMapper;
    private final ReservationTimeProvider timeProvider;
    private final NotificationService notificationService;

    /** 확정된 사전예약의 방문 날짜와 발급된 QR 유효시간을 함께 변경한다. */
    @Transactional
    public UpdateReservationVisitDateResponse changeVisitDate(
            Long reservationId,
            Long userId,
            UpdateReservationVisitDateRequest request
    ) {
        validateRequest(reservationId, userId, request);

        ReservationChangeReservationRow reservation = changeMapper.selectReservationForUpdate(reservationId);
        if (reservation == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        if (!ADVANCE.equals(reservation.getReservationType())
                || !CONFIRMED.equals(reservation.getStatus())) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }

        List<ReservationChangeFairDateRow> fairDates = changeMapper.selectFairDatesForUpdate(
                reservation.getFairId(),
                List.of(reservation.getVisitDate(), request.visitDate())
        );
        ReservationChangeFairDateRow currentDate = findDate(fairDates, reservation.getVisitDate());
        ReservationChangeFairDateRow targetDate = findDate(fairDates, request.visitDate());
        if (currentDate == null || targetDate == null) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        LocalDateTime now = timeProvider.now();
        LocalDateTime deadline = LocalDateTime.of(
                currentDate.getOperationDate(),
                currentDate.getEntryStartTime()
        ).minusHours(CHANGE_DEADLINE_HOURS);
        if (now.isAfter(deadline)) {
            throw new CommonException(ErrorCode.RESERVATION_CHANGE_DEADLINE_EXCEEDED);
        }

        if (request.visitDate().equals(reservation.getVisitDate())) {
            return response(reservationId, reservation.getVisitDate(), targetDate, reservation.getStatus());
        }
        if (!targetDate.getOperationDate().isAfter(timeProvider.today())) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        if (targetDate.getEntryStartTime() == null || targetDate.getEntryEndTime() == null
                || targetDate.getEntryEndTime().isBefore(targetDate.getEntryStartTime())) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        // 옮겨갈 날짜의 정원을 먼저 점유하고, 성공했을 때만 원래 날짜를 반납한다.
        // 순서를 뒤집으면 반납은 됐는데 점유에 실패하는 창이 생겨, 그 사이 원래 좌석을
        // 다른 사람이 채워버리면 되돌아갈 자리가 없어진다.
        //
        // 이 경로만 fair_dates 두 행을 동시에 만진다. 바로 위 selectFairDatesForUpdate가
        // 두 행을 날짜 오름차순으로 이미 잠갔으므로, 반대 방향(A→B와 B→A)의 동시 변경도
        // 같은 순서로 대기해 교착되지 않는다. 저빈도 경로라 이 잠금은 처리량에 영향이 없다.
        if (capacityMapper.occupy(reservation.getFairId(), request.visitDate()) != 1) {
            throw new CommonException(ErrorCode.RESERVATION_SOLD_OUT);
        }

        int updated = changeMapper.updateVisitDate(reservationId, request.visitDate(), now);
        if (updated != 1) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        capacityMapper.release(reservation.getFairId(), reservation.getVisitDate());
        entryMapper.updateEntryQrAvailability(
                reservationId,
                LocalDateTime.of(targetDate.getOperationDate(), targetDate.getEntryStartTime()),
                LocalDateTime.of(targetDate.getOperationDate(), targetDate.getEntryEndTime()),
                now
        );
        changeMapper.insertVisitDateChangedHistory(
                reservationId,
                reservation.getVisitDate(),
                request.visitDate(),
                userId,
                now
        );

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationService.save(new SaveNotificationDto.Request(
                            userId,
                            RecipientType.USER,
                            NotificationType.RESERVATION_CHANGED,
                            "예약 날짜가 변경되었습니다",
                            "방문 날짜가 " + request.visitDate() + "(으)로 변경되었습니다.",
                            null,
                            List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                            null
                    ));
                } catch (Exception e) {
                    log.error("예약 날짜 변경 알림 저장 실패. userId={}, reservationId={}", userId, reservationId, e);
                }
            }
        });

        return response(reservationId, reservation.getVisitDate(), targetDate, reservation.getStatus());
    }

    private void validateRequest(
            Long reservationId,
            Long userId,
            UpdateReservationVisitDateRequest request
    ) {
        if (reservationId == null || reservationId <= 0
                || userId == null || userId <= 0
                || request == null || request.visitDate() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ReservationChangeFairDateRow findDate(
            List<ReservationChangeFairDateRow> fairDates,
            LocalDate operationDate
    ) {
        return fairDates.stream()
                .filter(fairDate -> operationDate.equals(fairDate.getOperationDate()))
                .findFirst()
                .orElse(null);
    }

    private UpdateReservationVisitDateResponse response(
            Long reservationId,
            LocalDate previousVisitDate,
            ReservationChangeFairDateRow targetDate,
            String status
    ) {
        return new UpdateReservationVisitDateResponse(
                reservationId,
                previousVisitDate,
                targetDate.getOperationDate(),
                targetDate.getEntryStartTime(),
                targetDate.getEntryEndTime(),
                status
        );
    }
}
