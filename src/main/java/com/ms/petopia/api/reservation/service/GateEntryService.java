package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.EntryQrScanContext;
import com.ms.petopia.api.reservation.dto.GateScanResponse;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GateEntryService {

    private static final String FIRST_ENTRY = "FIRST_ENTRY";
    private static final String ALREADY_CHECKED_IN = "ALREADY_CHECKED_IN";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String FAIR_MISMATCH = "FAIR_MISMATCH";
    private static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String INVALID_RESERVATION_STATUS = "INVALID_RESERVATION_STATUS";
    private static final String DEFAULT_GATE_NAME = "MAIN_GATE";

    private final EntryMapper entryMapper;
    private final EntryQrTokenService tokenService;
    private final ReservationOperatorAccessService operatorAccessService;
    private final ReservationTimeProvider timeProvider;

    // 실시간 예약 현황 확인용
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게이트 QR 스캔을 처리한다. 유효하지 않은 QR도 예외 대신 결과 코드로 반환해
     * 같은 트랜잭션에서 스캔 감사 로그를 보존한다.
     */
    @Transactional
    public GateScanResponse scan(
            Long fairId,
            Long processedBy,
            String qrToken,
            String deviceInfo
    ) {
        validateRequest(fairId, processedBy, qrToken);
        operatorAccessService.assertCanManageFair(fairId, processedBy);

        LocalDateTime now = timeProvider.now();
        EntryQrScanContext context = entryMapper.selectQrForUpdate(tokenService.hash(qrToken));
        if (context == null) {
            logScan(null, null, fairId, NOT_FOUND, now, processedBy, deviceInfo);
            return new GateScanResponse(NOT_FOUND, false, null, null);
        }
        if (!fairId.equals(context.getFairId())) {
            logScan(context.getEntryQrId(), context.getReservationId(), fairId,
                    FAIR_MISMATCH, now, processedBy, deviceInfo);
            return new GateScanResponse(FAIR_MISMATCH, false, null, null);
        }
        if (now.isBefore(context.getAvailableFrom()) || now.isAfter(context.getExpiresAt())) {
            logScan(context.getEntryQrId(), context.getReservationId(), fairId,
                    NOT_AVAILABLE, now, processedBy, deviceInfo);
            return new GateScanResponse(NOT_AVAILABLE, false,
                    context.getReservationType(), context.getFirstCheckedInAt());
        }
        if (!("CONFIRMED".equals(context.getReservationStatus())
                || "CHECKED_IN".equals(context.getReservationStatus()))) {
            logScan(context.getEntryQrId(), context.getReservationId(), fairId,
                    INVALID_RESERVATION_STATUS, now, processedBy, deviceInfo);
            return new GateScanResponse(INVALID_RESERVATION_STATUS, false,
                    context.getReservationType(), context.getFirstCheckedInAt());
        }

        if (context.getEntryRecordId() != null) {
            entryMapper.updateEntryRecordForRescan(context.getEntryRecordId(), now);
            logScan(context.getEntryQrId(), context.getReservationId(), fairId,
                    ALREADY_CHECKED_IN, now, processedBy, deviceInfo);
            return new GateScanResponse(
                    ALREADY_CHECKED_IN,
                    false,
                    context.getReservationType(),
                    context.getFirstCheckedInAt()
            );
        }

        if (!"CONFIRMED".equals(context.getReservationStatus())) {
            logScan(context.getEntryQrId(), context.getReservationId(), fairId,
                    INVALID_RESERVATION_STATUS, now, processedBy, deviceInfo);
            return new GateScanResponse(INVALID_RESERVATION_STATUS, false,
                    context.getReservationType(), null);
        }

        entryMapper.insertEntryRecord(
                fairId,
                context.getReservationId(),
                context.getUserId(),
                context.getReservationType(),
                now,
                processedBy,
                DEFAULT_GATE_NAME
        );
        int checkedIn = entryMapper.markReservationCheckedIn(context.getReservationId(), now);
        if (checkedIn != 1) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        entryMapper.insertCheckedInHistory(context.getReservationId(), processedBy, now);
        logScan(context.getEntryQrId(), context.getReservationId(), fairId,
                FIRST_ENTRY, now, processedBy, deviceInfo);

        // 실시간 예약 현황 확인용
        eventPublisher.publishEvent(new ReservationStatusChangedEvent(fairId));

        return new GateScanResponse(FIRST_ENTRY, true, context.getReservationType(), now);
    }

    private void validateRequest(Long fairId, Long processedBy, String qrToken) {
        if (fairId == null || fairId <= 0
                || processedBy == null || processedBy <= 0
                || qrToken == null || qrToken.isBlank() || qrToken.length() > 500) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void logScan(
            Long entryQrId,
            Long reservationId,
            Long fairId,
            String resultCode,
            LocalDateTime now,
            Long processedBy,
            String deviceInfo
    ) {
        String safeDeviceInfo = deviceInfo != null && deviceInfo.length() > 200
                ? deviceInfo.substring(0, 200)
                : deviceInfo;
        entryMapper.insertGateScanLog(
                entryQrId,
                reservationId,
                fairId,
                resultCode,
                now,
                processedBy,
                DEFAULT_GATE_NAME,
                safeDeviceInfo
        );
    }
}
