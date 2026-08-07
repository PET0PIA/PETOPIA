package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.BoothScanContext;
import com.ms.petopia.api.reservation.dto.BoothScanResponse;
import com.ms.petopia.api.reservation.dto.BoothVisitRecord;
import com.ms.petopia.api.reservation.dto.EntryQrScanContext;
import com.ms.petopia.api.reservation.mapper.BoothVisitMapper;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 참가업체(VENDOR)가 자기 부스에서 관람객 입장 QR을 스캔해 부스 방문을 기록한다.
 * QR 검증은 게이트 스캔과 동일하며, (예약, 부스) 조합의 최초 스캔만 방문 1건을 만들고
 * 재스캔은 방문 횟수만 늘려 샘플·상품 중복 수령을 막는다.
 */
@Service
@RequiredArgsConstructor
public class BoothVisitService {

    private static final String FIRST_VISIT = "FIRST_VISIT";
    private static final String ALREADY_VISITED = "ALREADY_VISITED";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String FAIR_MISMATCH = "FAIR_MISMATCH";
    private static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String INVALID_RESERVATION_STATUS = "INVALID_RESERVATION_STATUS";

    private final BoothVisitMapper boothVisitMapper;
    private final EntryMapper entryMapper;
    private final EntryQrTokenService tokenService;
    private final ReservationTimeProvider timeProvider;

    /**
     * 부스 QR 스캔을 처리한다. 유효하지 않은 QR은 예외 대신 결과 코드로 반환한다.
     */
    @Transactional
    public BoothScanResponse scan(Long boothId, Long actorUserId, String qrToken) {
        validateRequest(boothId, actorUserId, qrToken);

        // 이 부스가 스캔하는 VENDOR의 소유인지 확인하고 행사·업체 식별자를 얻는다.
        BoothScanContext booth = boothVisitMapper.selectBoothForVendor(boothId, actorUserId);
        if (booth == null) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime now = timeProvider.now();
        EntryQrScanContext qr = entryMapper.selectQrForUpdate(tokenService.hash(qrToken));
        if (qr == null) {
            return new BoothScanResponse(NOT_FOUND, false, 0, null);
        }
        if (!booth.getFairId().equals(qr.getFairId())) {
            return new BoothScanResponse(FAIR_MISMATCH, false, 0, null);
        }
        if (now.isBefore(qr.getAvailableFrom()) || now.isAfter(qr.getExpiresAt())) {
            return new BoothScanResponse(NOT_AVAILABLE, false, 0, null);
        }
        if (!("CONFIRMED".equals(qr.getReservationStatus())
                || "CHECKED_IN".equals(qr.getReservationStatus()))) {
            return new BoothScanResponse(INVALID_RESERVATION_STATUS, false, 0, null);
        }

        BoothVisitRecord existing =
                boothVisitMapper.selectBoothVisitForUpdate(qr.getReservationId(), boothId);
        if (existing != null) {
            boothVisitMapper.updateBoothVisitForRevisit(existing.getBoothVisitId(), now);
            return new BoothScanResponse(
                    ALREADY_VISITED,
                    false,
                    existing.getVisitCount() + 1,
                    existing.getFirstVisitedAt()
            );
        }

        boothVisitMapper.insertBoothVisit(
                booth.getFairId(),
                boothId,
                booth.getBusinessId(),
                qr.getUserId(),
                qr.getReservationId(),
                now
        );
        return new BoothScanResponse(FIRST_VISIT, true, 1, now);
    }

    private void validateRequest(Long boothId, Long actorUserId, String qrToken) {
        if (boothId == null || boothId <= 0
                || actorUserId == null || actorUserId <= 0
                || qrToken == null || qrToken.isBlank() || qrToken.length() > 500) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
