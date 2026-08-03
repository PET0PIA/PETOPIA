package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.EntryQrIssueContext;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EntryQrService {

    private final EntryMapper entryMapper;
    private final EntryQrTokenService tokenService;
    private final ReservationTimeProvider timeProvider;

    /** 확정된 예약에 QR을 멱등 발급하고, 재호출 시 동일한 토큰을 반환한다. */
    @Transactional
    public String issueForReservation(Long reservationId) {
        if (reservationId == null || reservationId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        EntryQrIssueContext context = entryMapper.selectQrIssueContext(reservationId);
        if (context == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return issue(context);
    }

    /** 임시 사용자 헤더로 요청한 본인의 예약에만 QR을 반환한다. */
    @Transactional
    public String issueForUser(Long reservationId, Long userId) {
        if (reservationId == null || reservationId <= 0 || userId == null || userId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        EntryQrIssueContext context = entryMapper.selectQrIssueContext(reservationId);
        if (context == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!userId.equals(context.getUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        return issue(context);
    }

    private String issue(EntryQrIssueContext context) {
        if (!("CONFIRMED".equals(context.getReservationStatus())
                || "CHECKED_IN".equals(context.getReservationStatus()))) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (context.getEntryStartTime() == null || context.getEntryEndTime() == null
                || context.getEntryEndTime().isBefore(context.getEntryStartTime())) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Long reservationId = context.getReservationId();
        String token = tokenService.tokenForReservation(reservationId);
        if (!entryMapper.existsEntryQr(reservationId)) {
            LocalDateTime now = timeProvider.now();
            try {
                entryMapper.insertEntryQr(
                        reservationId,
                        tokenService.hash(token),
                        LocalDateTime.of(context.getVisitDate(), context.getEntryStartTime()),
                        LocalDateTime.of(context.getVisitDate(), context.getEntryEndTime()),
                        now
                );
            } catch (DuplicateKeyException exception) {
                // 같은 예약에 대한 동시 멱등 발급만 허용하고, 토큰 충돌 등 다른 제약 위반은 숨기지 않는다.
                if (!entryMapper.existsEntryQr(reservationId)) {
                    throw exception;
                }
            }
        }
        return token;
    }
}
