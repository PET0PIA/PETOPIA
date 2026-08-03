package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.EntryQrScanContext;
import com.ms.petopia.api.reservation.dto.GateScanResponse;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GateEntryServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long ADMIN_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private EntryMapper entryMapper;
    @Mock
    private EntryQrTokenService tokenService;
    @Mock
    private ReservationOperatorAccessService operatorAccessService;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private GateEntryService service;

    @Test
    void firstScanCreatesOneEntryAndChecksReservationIn() {
        givenCommonScan();
        given(entryMapper.selectQrForUpdate("hash")).willReturn(validContext(null));
        given(entryMapper.markReservationCheckedIn(30L, NOW)).willReturn(1);

        GateScanResponse response = service.scan(FAIR_ID, ADMIN_ID, "token", "A게이트", "device");

        assertThat(response.resultCode()).isEqualTo("FIRST_ENTRY");
        assertThat(response.firstEntry()).isTrue();
        assertThat(response.entrySource()).isEqualTo("ONSITE_DIRECT");
        verify(entryMapper).insertEntryRecord(
                FAIR_ID, 30L, 40L, "ONSITE_DIRECT", NOW, ADMIN_ID, "A게이트");
        verify(entryMapper).insertCheckedInHistory(30L, ADMIN_ID, NOW);
    }

    @Test
    void rescanOnlyUpdatesScanMetadataAndDoesNotCreateAnotherEntry() {
        givenCommonScan();
        EntryQrScanContext context = validContext(50L);
        context.setReservationStatus("CHECKED_IN");
        context.setFirstCheckedInAt(NOW.minusMinutes(1));
        given(entryMapper.selectQrForUpdate("hash")).willReturn(context);

        GateScanResponse response = service.scan(FAIR_ID, ADMIN_ID, "token", "A게이트", "device");

        assertThat(response.resultCode()).isEqualTo("ALREADY_CHECKED_IN");
        assertThat(response.firstEntry()).isFalse();
        verify(entryMapper).updateEntryRecordForRescan(50L, NOW);
    }

    @Test
    void unknownQrIsLoggedAndReturnedWithoutThrowing() {
        givenCommonScan();

        GateScanResponse response = service.scan(FAIR_ID, ADMIN_ID, "token", "A게이트", "device");

        assertThat(response.resultCode()).isEqualTo("NOT_FOUND");
        verify(entryMapper).insertGateScanLog(
                null, null, FAIR_ID, "NOT_FOUND", NOW, ADMIN_ID, "A게이트", "device");
    }

    private void givenCommonScan() {
        given(timeProvider.now()).willReturn(NOW);
        given(tokenService.hash("token")).willReturn("hash");
    }

    private EntryQrScanContext validContext(Long entryRecordId) {
        EntryQrScanContext context = new EntryQrScanContext();
        context.setEntryQrId(5L);
        context.setReservationId(30L);
        context.setFairId(FAIR_ID);
        context.setUserId(40L);
        context.setReservationType("ONSITE_DIRECT");
        context.setReservationStatus("CONFIRMED");
        context.setAvailableFrom(NOW.minusHours(1));
        context.setExpiresAt(NOW.plusHours(1));
        context.setEntryRecordId(entryRecordId);
        return context;
    }
}
