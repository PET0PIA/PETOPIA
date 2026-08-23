package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.BoothScanContext;
import com.ms.petopia.api.reservation.dto.BoothScanResponse;
import com.ms.petopia.api.reservation.dto.BoothVisitRecord;
import com.ms.petopia.api.reservation.dto.EntryQrScanContext;
import com.ms.petopia.api.reservation.mapper.BoothVisitMapper;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.global.exception.CommonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BoothVisitServiceTest {

    private static final Long BOOTH_ID = 100L;
    private static final Long VENDOR_ID = 20L;
    private static final Long FAIR_ID = 10L;
    private static final Long BUSINESS_ID = 7L;
    private static final Long RESERVATION_ID = 30L;
    private static final Long USER_ID = 40L;
    private static final String BOOTH_NAME = "멍냥사료 부스";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private BoothVisitMapper boothVisitMapper;
    @Mock
    private EntryMapper entryMapper;
    @Mock
    private EntryQrTokenService tokenService;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private BoothVisitService service;

    @BeforeEach
    void setUpTransactionSynchronization() {
        // 방문 체크인 알림이 afterCommit 콜백으로 미뤄지므로(FairCancelRequestServiceTest와
        // 동일한 이유), 활성 트랜잭션 동기화 컨텍스트가 있어야 registerSynchronization이
        // IllegalStateException 없이 통과한다.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDownTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void firstScanRecordsOneVisit() {
        givenOwnedBoothAndClock();
        given(entryMapper.selectQrForUpdate("hash")).willReturn(validQr());
        given(boothVisitMapper.selectBoothVisitForUpdate(RESERVATION_ID, BOOTH_ID)).willReturn(null);

        BoothScanResponse response = service.scan(BOOTH_ID, VENDOR_ID, "token");

        assertThat(response.resultCode()).isEqualTo("FIRST_VISIT");
        assertThat(response.firstVisit()).isTrue();
        assertThat(response.visitCount()).isEqualTo(1);
        assertThat(response.firstVisitedAt()).isEqualTo(NOW);
        verify(boothVisitMapper).insertBoothVisit(
                FAIR_ID, BOOTH_ID, BUSINESS_ID, USER_ID, RESERVATION_ID, NOW);

        // 최초 방문이면 QR 주인(관람객) 본인에게 체크인 알림이 가야 하는데, 그건 afterCommit
        // 콜백이라 트랜잭션이 실제로 커밋되기 전까지는 호출되지 않는다.
        verifyNoInteractions(notificationService);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor = ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.BOOTH_VISIT_CHECKED_IN);
        assertThat(notifCaptor.getValue().body()).contains(BOOTH_NAME);
        assertThat(notifCaptor.getValue().linkUrl()).isEqualTo("/booths/" + BOOTH_ID);
    }

    @Test
    void rescanIncrementsCountAndDoesNotCreateAnotherVisit() {
        givenOwnedBoothAndClock();
        given(entryMapper.selectQrForUpdate("hash")).willReturn(validQr());
        BoothVisitRecord existing = new BoothVisitRecord();
        existing.setBoothVisitId(500L);
        existing.setVisitCount(1);
        existing.setFirstVisitedAt(NOW.minusMinutes(5));
        given(boothVisitMapper.selectBoothVisitForUpdate(RESERVATION_ID, BOOTH_ID)).willReturn(existing);

        BoothScanResponse response = service.scan(BOOTH_ID, VENDOR_ID, "token");

        assertThat(response.resultCode()).isEqualTo("ALREADY_VISITED");
        assertThat(response.firstVisit()).isFalse();
        assertThat(response.visitCount()).isEqualTo(2);
        assertThat(response.firstVisitedAt()).isEqualTo(NOW.minusMinutes(5));
        verify(boothVisitMapper).updateBoothVisitForRevisit(500L, NOW);
        verify(boothVisitMapper, never()).insertBoothVisit(any(), any(), any(), any(), any(), any());

        // 재방문(재스캔)은 스탬프 투어처럼 반복될 수 있어 매번 알리지 않는다 - 커밋을 흉내내도
        // 알림이 등록돼 있지 않아야 한다.
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verifyNoInteractions(notificationService);
    }

    @Test
    void scanningBoothNotOwnedByVendorIsRejected() {
        given(boothVisitMapper.selectBoothForVendor(BOOTH_ID, VENDOR_ID)).willReturn(null);

        assertThatThrownBy(() -> service.scan(BOOTH_ID, VENDOR_ID, "token"))
                .isInstanceOf(CommonException.class);
        verifyNoInteractions(entryMapper);
    }

    @Test
    void unknownQrReturnsNotFoundWithoutRecordingVisit() {
        givenOwnedBoothAndClock();
        given(entryMapper.selectQrForUpdate("hash")).willReturn(null);

        BoothScanResponse response = service.scan(BOOTH_ID, VENDOR_ID, "token");

        assertThat(response.resultCode()).isEqualTo("NOT_FOUND");
        verify(boothVisitMapper, never()).insertBoothVisit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void qrFromAnotherFairIsRejected() {
        givenOwnedBoothAndClock();
        EntryQrScanContext qr = validQr();
        qr.setFairId(999L);
        given(entryMapper.selectQrForUpdate("hash")).willReturn(qr);

        BoothScanResponse response = service.scan(BOOTH_ID, VENDOR_ID, "token");

        assertThat(response.resultCode()).isEqualTo("FAIR_MISMATCH");
    }

    @Test
    void expiredQrIsNotAvailable() {
        givenOwnedBoothAndClock();
        EntryQrScanContext qr = validQr();
        qr.setExpiresAt(NOW.minusMinutes(1));
        given(entryMapper.selectQrForUpdate("hash")).willReturn(qr);

        BoothScanResponse response = service.scan(BOOTH_ID, VENDOR_ID, "token");

        assertThat(response.resultCode()).isEqualTo("NOT_AVAILABLE");
    }

    @Test
    void reservationNotConfirmedIsRejected() {
        givenOwnedBoothAndClock();
        EntryQrScanContext qr = validQr();
        qr.setReservationStatus("PENDING_PAYMENT");
        given(entryMapper.selectQrForUpdate("hash")).willReturn(qr);

        BoothScanResponse response = service.scan(BOOTH_ID, VENDOR_ID, "token");

        assertThat(response.resultCode()).isEqualTo("INVALID_RESERVATION_STATUS");
    }

    private void givenOwnedBoothAndClock() {
        given(boothVisitMapper.selectBoothForVendor(BOOTH_ID, VENDOR_ID)).willReturn(ownedBooth());
        given(timeProvider.now()).willReturn(NOW);
        given(tokenService.hash("token")).willReturn("hash");
    }

    private BoothScanContext ownedBooth() {
        BoothScanContext context = new BoothScanContext();
        context.setBoothId(BOOTH_ID);
        context.setBusinessId(BUSINESS_ID);
        context.setFairId(FAIR_ID);
        context.setName(BOOTH_NAME);
        return context;
    }

    private EntryQrScanContext validQr() {
        EntryQrScanContext qr = new EntryQrScanContext();
        qr.setEntryQrId(5L);
        qr.setReservationId(RESERVATION_ID);
        qr.setFairId(FAIR_ID);
        qr.setUserId(USER_ID);
        qr.setReservationType("ADVANCE");
        qr.setReservationStatus("CONFIRMED");
        qr.setAvailableFrom(NOW.minusHours(1));
        qr.setExpiresAt(NOW.plusHours(1));
        return qr;
    }
}
