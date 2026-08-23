package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.reservation.dto.ReservationChangeFairDateRow;
import com.ms.petopia.api.reservation.dto.ReservationChangeReservationRow;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationChangeMapper;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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

    private final ReservationChangeMapper changeMapper;
    // 중복 예약 판정(existsActiveReservation)을 예약 생성 경로와 공유하려고 함께 주입한다.
    // 상태 집합을 여기서 다시 적으면 한쪽만 바뀌었을 때 조용히 어긋난다.
    private final ReservationMapper reservationMapper;
    private final ReservationCapacityMapper capacityMapper;
    private final EntryMapper entryMapper;
    private final ReservationTimeProvider timeProvider;
    private final NotificationService notificationService;
    private final ReservationPetService reservationPetService;
    private final AuthMapper authMapper;
    private final MailService mailService;
    private final PaymentMapper paymentMapper;

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
        // 마감은 행사가 정한다(fairs.reservation_change_deadline_hours). 설정이 없으면 기본 12시간.
        // 여기서 12를 고정하면 행사 신청서에 입력한 "변경 가능 기한"이 조용히 무시된다 -
        // 취소(ReservationCancellationService.validateCancelable)와 같은 규칙으로 맞춘다.
        Integer configuredDeadlineHours = changeMapper.selectChangeDeadlineHours(reservation.getFairId());
        int deadlineHours = configuredDeadlineHours == null
                ? ReservationDeadlinePolicy.DEFAULT_CHANGE_DEADLINE_HOURS
                : configuredDeadlineHours;
        if (deadlineHours < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime deadline = LocalDateTime.of(
                currentDate.getOperationDate(),
                currentDate.getEntryStartTime()
        ).minusHours(deadlineHours);
        if (now.isAfter(deadline)) {
            throw new CommonException(ErrorCode.RESERVATION_CHANGE_DEADLINE_EXCEEDED);
        }

        // 날짜가 그대로여도 반려동물만 바꾸려는 요청일 수 있다 - 정원·QR·이력은 건드리지 않고
        // 동반 정보만 교체한 뒤 현재 정보를 돌려준다.
        if (request.visitDate().equals(reservation.getVisitDate())) {
            replacePetsIfRequested(reservationId, userId, reservation.getFairId(), request);
            return response(reservationId, reservation.getVisitDate(), targetDate, reservation.getStatus());
        }
        if (!targetDate.getOperationDate().isAfter(timeProvider.today())) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        if (targetDate.getEntryStartTime() == null || targetDate.getEntryEndTime() == null
                || targetDate.getEntryEndTime().isBefore(targetDate.getEntryStartTime())) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        // 옮겨갈 날짜에 내 활성 예약이 이미 있으면 여기서 거절한다.
        //
        // reservations에는 (사용자·행사·방문일) 활성 예약을 하나로 강제하는 유니크 제약
        // (UK_RESERVATION_ACTIVE_USER_FAIR_DATE)이 걸려 있다. 이 검사가 없으면 아래 UPDATE가
        // 그 제약에 걸려 DuplicateKeyException이 그대로 올라가고, 사용자에게는 500 서버 오류로
        // 보인다 - 실제로는 "그 날짜엔 이미 예약이 있다"는 평범한 거절이다.
        // 정원 점유 전에 검사한다. 점유부터 하면 실패했을 때 매진(R004)으로 잘못 보일 수 있다.
        if (reservationMapper.existsActiveReservation(reservation.getFairId(), userId, request.visitDate())) {
            throw new CommonException(ErrorCode.DUPLICATED_RESERVATION);
        }

        // 옮겨갈 날짜의 정원을 먼저 점유하고, 성공했을 때만 원래 날짜를 반납한다.
        // 순서를 뒤집으면 반납은 됐는데 점유에 실패하는 창이 생겨, 그 사이 원래 좌석을
        // 다른 사람이 채워버리면 되돌아갈 자리가 없어진다.
        //
        // 이 경로만 fair_dates 두 행을 동시에 만진다. 바로 위 selectFairDatesForUpdate가
        // FORCE INDEX (UK_FAIR_DATE_FAIR_DATE)로 스캔 순서를 날짜 오름차순에 고정해 두 행을
        // 이미 잠갔으므로, 반대 방향(A→B와 B→A)의 동시 변경도 같은 순서로 대기해 교착되지
        // 않는다. 저빈도 경로라 이 잠금은 처리량에 영향이 없다.
        if (capacityMapper.occupy(reservation.getFairId(), request.visitDate()) != 1) {
            throw new CommonException(ErrorCode.RESERVATION_SOLD_OUT);
        }

        // 위 사전 검사와 이 UPDATE 사이에 같은 사용자가 그 날짜를 새로 예약할 수 있다.
        // 최종 판정은 DB 제약이 하고, 여기서는 그 위반을 500이 아닌 R005로 번역만 한다
        // (예약 생성 경로 ReservationService.create와 같은 처리다).
        int updated;
        try {
            updated = changeMapper.updateVisitDate(reservationId, request.visitDate(), now);
        } catch (DuplicateKeyException e) {
            if (ReservationConstraintViolations.isActiveReservationDuplicate(e)) {
                throw new CommonException(ErrorCode.DUPLICATED_RESERVATION, e);
            }
            throw e;
        }
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
        replacePetsIfRequested(reservationId, userId, reservation.getFairId(), request);

        LocalDate previousVisitDate = reservation.getVisitDate();
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
                            "/reservations/me/" + reservationId,
                            List.of(DeliveryChannel.IN_APP),
                            null
                    ));
                } catch (Exception e) {
                    log.error("예약 날짜 변경 알림 저장 실패. userId={}, reservationId={}", userId, reservationId, e);
                }
                try {
                    User user = authMapper.selectUserById(userId);
                    if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                        String fairName = paymentMapper.selectFairNameById(reservation.getFairId());
                        mailService.sendReservationChangedEmail(user.getEmail(), fairName, previousVisitDate,
                                request.visitDate(), targetDate.getEntryStartTime(), targetDate.getEntryEndTime());
                    }
                } catch (Exception e) {
                    log.error("예약 날짜 변경 이메일 발송 실패. userId={}, reservationId={}", userId, reservationId, e);
                }
            }
        });

        return response(reservationId, reservation.getVisitDate(), targetDate, reservation.getStatus());
    }

    /**
     * 요청에 petIds가 있을 때만 동반 정보를 교체한다. null이면 손대지 않는다 -
     * "날짜만 바꾸려는 요청"이 동반 정보를 조용히 지워버리면 안 된다.
     *
     * <p>동반 허용 여부는 여기서 따로 조회한다. 위의 예약 조회는 reservations에
     * {@code FOR UPDATE}를 걸고 있어, 같은 쿼리에 fairs를 조인하면 잠금 범위가 넓어진다.
     */
    private void replacePetsIfRequested(
            Long reservationId,
            Long userId,
            Long fairId,
            UpdateReservationVisitDateRequest request
    ) {
        if (request.petIds() == null) {
            return;
        }
        reservationPetService.replacePets(
                reservationId,
                userId,
                reservationPetService.isFairPetAllowed(fairId),
                request.petIds()
        );
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
