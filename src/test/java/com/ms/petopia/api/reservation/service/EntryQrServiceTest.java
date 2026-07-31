package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.EntryQrIssueContext;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EntryQrServiceTest {

    @Mock
    private EntryMapper entryMapper;
    @Mock
    private EntryQrTokenService tokenService;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private EntryQrService service;

    @Test
    void issuesDeterministicQrForConfirmedReservation() {
        EntryQrIssueContext context = new EntryQrIssueContext();
        context.setReservationId(10L);
        context.setReservationStatus("CONFIRMED");
        context.setVisitDate(LocalDate.of(2026, 8, 1));
        context.setEntryStartTime(LocalTime.of(9, 0));
        context.setEntryEndTime(LocalTime.of(18, 0));
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 8, 0);
        given(entryMapper.selectQrIssueContext(10L)).willReturn(context);
        given(tokenService.tokenForReservation(10L)).willReturn("token");
        given(tokenService.hash("token")).willReturn("hash");
        given(timeProvider.now()).willReturn(now);

        assertThat(service.issueForReservation(10L)).isEqualTo("token");
        verify(entryMapper).insertEntryQr(
                10L,
                "hash",
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 1, 18, 0),
                now
        );
    }
}
