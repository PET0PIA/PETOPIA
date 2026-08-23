package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.BoothScanContext;
import com.ms.petopia.api.reservation.dto.BoothScanResponse;
import com.ms.petopia.api.reservation.dto.BoothVisitRecord;
import com.ms.petopia.api.reservation.dto.EntryQrScanContext;
import com.ms.petopia.api.reservation.mapper.BoothVisitMapper;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 참가업체(VENDOR)가 자기 부스에서 관람객 입장 QR을 스캔해 부스 방문을 기록한다.
 * QR 검증은 게이트 스캔과 동일하며, (예약, 부스) 조합의 최초 스캔만 방문 1건을 만들고
 * 재스캔은 방문 횟수만 늘려 샘플·상품 중복 수령을 막는다.
 */
@Slf4j
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
    private final NotificationService notificationService;

    /**
     * 부스 QR 스캔을 처리한다. 유효하지 않은 QR은 예외 대신 결과 코드로 반환한다.
     */
    @Transactional
    public BoothScanResponse scan(Long boothId, Long actorUserId, String qrToken) {
        validateRequest(boothId, actorUserId, qrToken);

        // 이 부스가 스캔하는 VENDOR의 소유인지 확인하고 행사·사업자 식별자를 얻는다.
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
        notifyVisitorCheckedInAfterCommit(qr.getUserId(), boothId, booth.getName());
        return new BoothScanResponse(FIRST_VISIT, true, 1, now);
    }

    /**
     * 부스 방문(최초 스캔)이 기록됐음을 QR 주인(관람객) 본인에게 알린다. 재스캔(재방문)은
     * 알리지 않는다 - 스탬프 투어처럼 같은 부스를 여러 번 스캔하는 경우 매번 알림이 오면
     * 시끄러워서, 첫 방문 확인 용도로만 쓴다.
     */
    private void notifyVisitorCheckedInAfterCommit(Long visitorUserId, Long boothId, String boothName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationService.save(new SaveNotificationDto.Request(
                            visitorUserId,
                            RecipientType.USER,
                            NotificationType.BOOTH_VISIT_CHECKED_IN,
                            "부스 방문이 확인됐어요",
                            "'" + boothName + "' 부스 방문이 확인됐어요.",
                            "/booths/" + boothId,
                            List.of(DeliveryChannel.IN_APP),
                            null
                    ));
                } catch (Exception e) {
                    log.error("부스 방문 체크인 알림 저장 실패. boothId={}, visitorUserId={}", boothId, visitorUserId, e);
                }
            }
        });
    }

    private void validateRequest(Long boothId, Long actorUserId, String qrToken) {
        if (boothId == null || boothId <= 0
                || actorUserId == null || actorUserId <= 0
                || qrToken == null || qrToken.isBlank() || qrToken.length() > 500) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
