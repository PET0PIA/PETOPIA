package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.EntryQrIssueContext;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EntryQrServiceTest {

    private static final Long RESERVATION_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 8, 0);

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
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(issuableContext("CONFIRMED"));
        given(tokenService.tokenForReservation(RESERVATION_ID)).willReturn("token");
        given(tokenService.hash("token")).willReturn("hash");
        given(timeProvider.now()).willReturn(NOW);

        assertThat(service.issueForReservation(RESERVATION_ID)).isEqualTo("token");
        verify(entryMapper).insertEntryQr(
                RESERVATION_ID,
                "hash",
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 1, 18, 0),
                NOW
        );
    }

    @Test
    @DisplayName("이미 입장한 예약도 같은 QR을 다시 받을 수 있다")
    void issuesQrForCheckedInReservation() {
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(issuableContext("CHECKED_IN"));
        given(tokenService.tokenForReservation(RESERVATION_ID)).willReturn("token");
        given(entryMapper.existsEntryQr(RESERVATION_ID)).willReturn(true);
        given(timeProvider.now()).willReturn(NOW);

        assertThat(service.issueForReservation(RESERVATION_ID)).isEqualTo("token");
    }

    @Test
    @DisplayName("이미 발급된 QR이 있으면 다시 저장하지 않고 같은 토큰을 반환한다")
    void reusesExistingQrWithoutInserting() {
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(issuableContext("CONFIRMED"));
        given(tokenService.tokenForReservation(RESERVATION_ID)).willReturn("token");
        given(entryMapper.existsEntryQr(RESERVATION_ID)).willReturn(true);
        given(timeProvider.now()).willReturn(NOW);

        assertThat(service.issueForReservation(RESERVATION_ID)).isEqualTo("token");

        verify(entryMapper, never()).insertEntryQr(any(), any(), any(), any(), any());
        verify(timeProvider).now();
    }

    @Test
    @DisplayName("입장 종료 시각이 지나면 확정 예약도 QR을 반환하지 않는다")
    void rejectsQrAfterEntryEnds() {
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(issuableContext("CONFIRMED"));
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 18, 0, 1));

        assertErrorCode(
                () -> service.issueForReservation(RESERVATION_ID),
                ErrorCode.ENTRY_QR_NOT_AVAILABLE
        );

        verify(tokenService, never()).tokenForReservation(any());
        verify(entryMapper, never()).insertEntryQr(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 대기처럼 확정되지 않은 예약에는 QR을 발급하지 않는다")
    void rejectsQrForNotConfirmedReservation() {
        given(entryMapper.selectQrIssueContext(RESERVATION_ID))
                .willReturn(issuableContext("PENDING_PAYMENT"));

        assertErrorCode(
                () -> service.issueForReservation(RESERVATION_ID),
                ErrorCode.RESERVATION_STATUS_CONFLICT
        );

        verify(entryMapper, never()).insertEntryQr(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("입장 시간이 설정되지 않았으면 QR을 발급하지 않는다")
    void rejectsQrWhenEntryTimeIsMissing() {
        EntryQrIssueContext context = issuableContext("CONFIRMED");
        context.setEntryEndTime(null);
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(context);

        assertErrorCode(
                () -> service.issueForReservation(RESERVATION_ID),
                ErrorCode.INVALID_INPUT_VALUE
        );

        verify(entryMapper, never()).insertEntryQr(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("입장 종료 시간이 시작 시간보다 이르면 QR을 발급하지 않는다")
    void rejectsQrWhenEntryEndTimeIsBeforeStartTime() {
        EntryQrIssueContext context = issuableContext("CONFIRMED");
        context.setEntryStartTime(LocalTime.of(18, 0));
        context.setEntryEndTime(LocalTime.of(9, 0));
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(context);

        assertErrorCode(
                () -> service.issueForReservation(RESERVATION_ID),
                ErrorCode.INVALID_INPUT_VALUE
        );

        verify(entryMapper, never()).insertEntryQr(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("예약을 찾을 수 없으면 예약 없음 예외를 던진다")
    void rejectsQrForUnknownReservation() {
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(null);

        assertErrorCode(
                () -> service.issueForReservation(RESERVATION_ID),
                ErrorCode.RESERVATION_NOT_FOUND
        );
    }

    @Test
    void rejectsQrRequestFromAnotherUser() {
        EntryQrIssueContext context = new EntryQrIssueContext();
        context.setReservationId(RESERVATION_ID);
        context.setUserId(USER_ID);
        given(entryMapper.selectQrIssueContext(RESERVATION_ID)).willReturn(context);

        assertErrorCode(() -> service.issueForUser(RESERVATION_ID, 99L), ErrorCode.ACCESS_DENIED);
    }

    private EntryQrIssueContext issuableContext(String reservationStatus) {
        EntryQrIssueContext context = new EntryQrIssueContext();
        context.setReservationId(RESERVATION_ID);
        context.setUserId(USER_ID);
        context.setReservationStatus(reservationStatus);
        context.setVisitDate(VISIT_DATE);
        context.setEntryStartTime(LocalTime.of(9, 0));
        context.setEntryEndTime(LocalTime.of(18, 0));
        return context;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
