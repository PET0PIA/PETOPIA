package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent;
import com.ms.petopia.api.statistics.mapper.ReservationDashboardMapper;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationDashboardServiceTest {

    private static final Long FAIR_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 1);

    @Mock
    private ReservationDashboardMapper dashboardMapper;

    @Mock
    private DashboardEmitterRegistry emitterRegistry;

    @InjectMocks
    private ReservationDashboardService dashboardService;

    // ── getDateSummary ────────────────────────────────────────────────

    @Test
    @DisplayName("date 없으면 Mapper에 null 전달해 전체 날짜 조회")
    void getDateSummary_noDate_passesNullToMapper() {
        List<ReservationDateSummaryDto> expected = List.of(
                new ReservationDateSummaryDto(),
                new ReservationDateSummaryDto()
        );
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, null)).willReturn(expected);

        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(FAIR_ID, null);

        assertThat(result).hasSize(2);
        then(dashboardMapper).should(times(1)).selectDateSummaryList(FAIR_ID, null);
    }

    @Test
    @DisplayName("date 있으면 해당 날짜를 Mapper에 그대로 전달")
    void getDateSummary_withDate_passesDateToMapper() {
        List<ReservationDateSummaryDto> expected = List.of(new ReservationDateSummaryDto());
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, TARGET_DATE)).willReturn(expected);

        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(FAIR_ID, TARGET_DATE);

        assertThat(result).hasSize(1);
        then(dashboardMapper).should(times(1)).selectDateSummaryList(FAIR_ID, TARGET_DATE);
    }

    @Test
    @DisplayName("존재하지 않는 fairId면 빈 리스트 반환")
    void getDateSummary_notExistFairId_returnsEmptyList() {
        given(dashboardMapper.selectDateSummaryList(999L, null)).willReturn(List.of());

        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(999L, null);

        assertThat(result).isEmpty();
    }

    // ── onReservationStatusChanged ────────────────────────────────────

    @Test
    @DisplayName("연결된 클라이언트가 없으면 DB 조회 자체를 건너뜀")
    void onReservationStatusChanged_noClients_skipsDbQuery() {
        given(emitterRegistry.getEmitters(FAIR_ID)).willReturn(List.of());

        dashboardService.onReservationStatusChanged(new ReservationStatusChangedEvent(FAIR_ID));

        then(dashboardMapper).should(never()).selectDateSummaryList(any(), any());
    }

    @Test
    @DisplayName("연결된 클라이언트가 있으면 최신 데이터를 push")
    void onReservationStatusChanged_withClients_pushesLatestData() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        given(emitterRegistry.getEmitters(FAIR_ID)).willReturn(List.of(emitter));
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, null))
                .willReturn(List.of(new ReservationDateSummaryDto()));

        dashboardService.onReservationStatusChanged(new ReservationStatusChangedEvent(FAIR_ID));

        then(emitter).should(times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("여러 클라이언트가 연결됐으면 모두에게 push")
    void onReservationStatusChanged_multipleClients_pushesToAll() throws IOException {
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        given(emitterRegistry.getEmitters(FAIR_ID)).willReturn(List.of(emitter1, emitter2));
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, null)).willReturn(List.of());

        dashboardService.onReservationStatusChanged(new ReservationStatusChangedEvent(FAIR_ID));

        then(emitter1).should(times(1)).send(any(SseEmitter.SseEventBuilder.class));
        then(emitter2).should(times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("한 emitter 전송 실패해도 나머지 emitter에는 계속 push")
    void onReservationStatusChanged_oneEmitterFails_continuesOthers() throws IOException {
        SseEmitter failingEmitter = mock(SseEmitter.class);
        SseEmitter okEmitter = mock(SseEmitter.class);
        given(emitterRegistry.getEmitters(FAIR_ID)).willReturn(List.of(failingEmitter, okEmitter));
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, null)).willReturn(List.of());
        doThrow(new IOException("연결 끊김"))
                .when(failingEmitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatNoException().isThrownBy(() ->
                dashboardService.onReservationStatusChanged(new ReservationStatusChangedEvent(FAIR_ID))
        );

        then(okEmitter).should(times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
