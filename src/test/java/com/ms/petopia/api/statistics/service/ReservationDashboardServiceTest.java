package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.statistics.dto.BoothVisitStatDto;
import com.ms.petopia.api.statistics.dto.HourlyEntryTrendDto;
import com.ms.petopia.api.statistics.dto.LabelCountDto;
import com.ms.petopia.api.statistics.dto.PetBreedStatDto;
import com.ms.petopia.api.statistics.dto.QrIssuanceSummaryDto;
import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.dto.VisitStatsDto;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent;
import com.ms.petopia.api.statistics.mapper.ReservationDashboardMapper;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationDashboardServiceTest {

    private static final Long FAIR_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 1);

    @Mock
    private ReservationDashboardMapper dashboardMapper;

    @Mock
    private DashboardEmitterRegistry emitterRegistry;

    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @InjectMocks
    private ReservationDashboardService dashboardService;

    private void assertAccessDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

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

    // ── getQrIssuanceSummary ──────────────────────────────────────────

    @Test
    @DisplayName("QR 발급 통계 - Mapper 결과를 그대로 반환")
    void getQrIssuanceSummary_returnsMapperResult() {
        QrIssuanceSummaryDto dto = new QrIssuanceSummaryDto();
        dto.setOperationDate(TARGET_DATE);
        dto.setQrIssuedCount(100);
        dto.setQrActiveCount(80);
        dto.setQrRevokedCount(20);
        given(dashboardMapper.selectQrIssuanceSummary(FAIR_ID)).willReturn(List.of(dto));

        List<QrIssuanceSummaryDto> result = dashboardService.getQrIssuanceSummary(FAIR_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQrIssuedCount()).isEqualTo(100);
        assertThat(result.get(0).getQrActiveCount()).isEqualTo(80);
        assertThat(result.get(0).getQrRevokedCount()).isEqualTo(20);
        then(dashboardMapper).should(times(1)).selectQrIssuanceSummary(FAIR_ID);
    }

    @Test
    @DisplayName("QR 발급 통계 - QR이 없는 날짜면 빈 리스트 반환")
    void getQrIssuanceSummary_noQrIssued_returnsEmptyList() {
        given(dashboardMapper.selectQrIssuanceSummary(FAIR_ID)).willReturn(List.of());

        List<QrIssuanceSummaryDto> result = dashboardService.getQrIssuanceSummary(FAIR_ID);

        assertThat(result).isEmpty();
        then(dashboardMapper).should(times(1)).selectQrIssuanceSummary(FAIR_ID);
    }

    @Test
    @DisplayName("QR 발급 통계 - 존재하지 않는 fairId면 빈 리스트 반환")
    void getQrIssuanceSummary_unknownFairId_returnsEmptyList() {
        given(dashboardMapper.selectQrIssuanceSummary(999L)).willReturn(List.of());

        List<QrIssuanceSummaryDto> result = dashboardService.getQrIssuanceSummary(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("QR 발급 통계 - 담당 행사가 아닌 EVENT_ADMIN이면 ACCESS_DENIED를 던지고 Mapper를 호출하지 않는다")
    void getQrIssuanceSummary_담당행사아니면_예외를_던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertAccessDenied(() -> dashboardService.getQrIssuanceSummary(FAIR_ID));
        then(dashboardMapper).should(never()).selectQrIssuanceSummary(any());
    }

    // ── getHourlyEntryTrend ───────────────────────────────────────────

    @Test
    @DisplayName("시간대별 입장 추이 - 날짜와 fairId를 Mapper에 그대로 전달")
    void getHourlyEntryTrend_passesParamsToMapper() {
        HourlyEntryTrendDto hour10 = makeHourlyDto(10, 30);
        HourlyEntryTrendDto hour11 = makeHourlyDto(11, 55);
        given(dashboardMapper.selectHourlyEntryTrend(FAIR_ID, TARGET_DATE))
                .willReturn(List.of(hour10, hour11));

        List<HourlyEntryTrendDto> result = dashboardService.getHourlyEntryTrend(FAIR_ID, TARGET_DATE);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEntryHour()).isEqualTo(10);
        assertThat(result.get(0).getEntryCount()).isEqualTo(30);
        assertThat(result.get(1).getEntryHour()).isEqualTo(11);
        assertThat(result.get(1).getEntryCount()).isEqualTo(55);
        then(dashboardMapper).should(times(1)).selectHourlyEntryTrend(FAIR_ID, TARGET_DATE);
    }

    @Test
    @DisplayName("시간대별 입장 추이 - 입장 기록 없는 날짜면 빈 리스트 반환")
    void getHourlyEntryTrend_noEntries_returnsEmptyList() {
        given(dashboardMapper.selectHourlyEntryTrend(FAIR_ID, TARGET_DATE)).willReturn(List.of());

        List<HourlyEntryTrendDto> result = dashboardService.getHourlyEntryTrend(FAIR_ID, TARGET_DATE);

        assertThat(result).isEmpty();
        then(dashboardMapper).should(times(1)).selectHourlyEntryTrend(FAIR_ID, TARGET_DATE);
    }

    @Test
    @DisplayName("시간대별 입장 추이 - 여러 날짜를 다르게 조회하면 각각 독립적으로 Mapper 호출")
    void getHourlyEntryTrend_differentDates_callsMapperSeparately() {
        LocalDate date2 = LocalDate.of(2026, 8, 2);
        given(dashboardMapper.selectHourlyEntryTrend(FAIR_ID, TARGET_DATE))
                .willReturn(List.of(makeHourlyDto(10, 20)));
        given(dashboardMapper.selectHourlyEntryTrend(FAIR_ID, date2))
                .willReturn(List.of(makeHourlyDto(14, 45)));

        List<HourlyEntryTrendDto> result1 = dashboardService.getHourlyEntryTrend(FAIR_ID, TARGET_DATE);
        List<HourlyEntryTrendDto> result2 = dashboardService.getHourlyEntryTrend(FAIR_ID, date2);

        assertThat(result1.get(0).getEntryHour()).isEqualTo(10);
        assertThat(result2.get(0).getEntryHour()).isEqualTo(14);
        then(dashboardMapper).should(times(1)).selectHourlyEntryTrend(FAIR_ID, TARGET_DATE);
        then(dashboardMapper).should(times(1)).selectHourlyEntryTrend(FAIR_ID, date2);
    }

    @Test
    @DisplayName("시간대별 입장 추이 - 담당 행사가 아닌 EVENT_ADMIN이면 ACCESS_DENIED를 던지고 Mapper를 호출하지 않는다")
    void getHourlyEntryTrend_담당행사아니면_예외를_던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertAccessDenied(() -> dashboardService.getHourlyEntryTrend(FAIR_ID, TARGET_DATE));
        then(dashboardMapper).should(never()).selectHourlyEntryTrend(any(), any());
    }

    // ── getBoothVisitStats ────────────────────────────────────────────

    @Test
    @DisplayName("부스 방문 통계 - Mapper 결과를 그대로 반환")
    void getBoothVisitStats_returnsMapperResult() {
        BoothVisitStatDto dto = new BoothVisitStatDto();
        dto.setBoothId(10L);
        dto.setBoothNumber("A-01");
        dto.setBoothName("펫샵 강남");
        dto.setUniqueVisitorCount(150);
        dto.setTotalScanCount(160);
        given(dashboardMapper.selectBoothVisitStats(FAIR_ID)).willReturn(List.of(dto));

        List<BoothVisitStatDto> result = dashboardService.getBoothVisitStats(FAIR_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBoothNumber()).isEqualTo("A-01");
        assertThat(result.get(0).getUniqueVisitorCount()).isEqualTo(150);
        assertThat(result.get(0).getTotalScanCount()).isEqualTo(160);
        then(dashboardMapper).should(times(1)).selectBoothVisitStats(FAIR_ID);
    }

    @Test
    @DisplayName("부스 방문 통계 - 방문 기록 없으면 빈 리스트 반환")
    void getBoothVisitStats_noVisits_returnsEmptyList() {
        given(dashboardMapper.selectBoothVisitStats(FAIR_ID)).willReturn(List.of());

        List<BoothVisitStatDto> result = dashboardService.getBoothVisitStats(FAIR_ID);

        assertThat(result).isEmpty();
        then(dashboardMapper).should(times(1)).selectBoothVisitStats(FAIR_ID);
    }

    @Test
    @DisplayName("부스 방문 통계 - 여러 부스가 있으면 전부 반환")
    void getBoothVisitStats_multipleBooths_returnsAll() {
        BoothVisitStatDto booth1 = makeBoothDto(1L, "A-01", 200);
        BoothVisitStatDto booth2 = makeBoothDto(2L, "A-02", 150);
        BoothVisitStatDto booth3 = makeBoothDto(3L, "B-01", 50);
        given(dashboardMapper.selectBoothVisitStats(FAIR_ID))
                .willReturn(List.of(booth1, booth2, booth3));

        List<BoothVisitStatDto> result = dashboardService.getBoothVisitStats(FAIR_ID);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getUniqueVisitorCount()).isEqualTo(200);
        assertThat(result.get(1).getUniqueVisitorCount()).isEqualTo(150);
        assertThat(result.get(2).getUniqueVisitorCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("부스 방문 통계 - 담당 행사가 아닌 EVENT_ADMIN이면 ACCESS_DENIED를 던지고 Mapper를 호출하지 않는다")
    void getBoothVisitStats_담당행사아니면_예외를_던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertAccessDenied(() -> dashboardService.getBoothVisitStats(FAIR_ID));
        then(dashboardMapper).should(never()).selectBoothVisitStats(any());
    }

    // ── getBoothVisitPatternDistribution ────────────────────────────────

    @Test
    @DisplayName("부스 방문 패턴 분포 - 담당 행사가 아닌 EVENT_ADMIN이면 ACCESS_DENIED를 던지고 Mapper를 호출하지 않는다")
    void getBoothVisitPatternDistribution_담당행사아니면_예외를_던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertAccessDenied(() -> dashboardService.getBoothVisitPatternDistribution(FAIR_ID));
        then(dashboardMapper).should(never()).selectBoothVisitPatternDistribution(any());
    }

    // ── getVisitStats ─────────────────────────────────────────────────

    @Test
    @DisplayName("방문자 있고 예약도 있을 때 visitRate를 소수점 1자리로 계산")
    void getVisitStats_withVisitors_calculatesVisitRateCorrectly() {
        stubAllMapperMethods(80, 100);

        VisitStatsDto result = dashboardService.getVisitStats(FAIR_ID);

        assertThat(result.getTotalVisitors()).isEqualTo(80);
        assertThat(result.getTotalConfirmedReservations()).isEqualTo(100);
        assertThat(result.getVisitRate()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("방문율 소수점 반올림 - 3/7 → 42.9%")
    void getVisitStats_fractionalVisitRate_roundsToOneDecimal() {
        stubAllMapperMethods(3, 7);

        VisitStatsDto result = dashboardService.getVisitStats(FAIR_ID);

        assertThat(result.getVisitRate()).isEqualTo(42.9);
    }

    @Test
    @DisplayName("확정 예약이 0건이면 visitRate는 0.0 (ZeroDivisionError 방지)")
    void getVisitStats_noConfirmedReservations_visitRateIsZero() {
        stubAllMapperMethods(0, 0);

        VisitStatsDto result = dashboardService.getVisitStats(FAIR_ID);

        assertThat(result.getVisitRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("반려동물 나이 데이터 없으면 avgPetAge는 null")
    void getVisitStats_noPetAgeData_avgPetAgeIsNull() {
        stubAllMapperMethods(10, 10);
        given(dashboardMapper.selectAvgPetAge(FAIR_ID)).willReturn(null);

        VisitStatsDto result = dashboardService.getVisitStats(FAIR_ID);

        assertThat(result.getAvgPetAge()).isNull();
    }

    @Test
    @DisplayName("방문자 특징 데이터가 있으면 각 breakdown에 올바르게 담김")
    void getVisitStats_withBreakdownData_returnsAllBreakdowns() {
        stubAllMapperMethods(100, 100);
        given(dashboardMapper.selectGenderBreakdown(FAIR_ID))
                .willReturn(List.of(makeLabelCount("MALE", 60), makeLabelCount("FEMALE", 40)));
        given(dashboardMapper.selectPetSpeciesBreakdown(FAIR_ID))
                .willReturn(List.of(makeLabelCount("DOG", 70), makeLabelCount("CAT", 30)));
        given(dashboardMapper.selectPetBreedBreakdown(FAIR_ID))
                .willReturn(List.of(makePetBreed("DOG", "골든 리트리버", 25)));
        given(dashboardMapper.selectAvgPetAge(FAIR_ID)).willReturn(3.5);

        VisitStatsDto result = dashboardService.getVisitStats(FAIR_ID);

        assertThat(result.getGenderBreakdown()).hasSize(2);
        assertThat(result.getGenderBreakdown().get(0).getLabel()).isEqualTo("MALE");
        assertThat(result.getGenderBreakdown().get(0).getCount()).isEqualTo(60);
        assertThat(result.getPetSpeciesBreakdown()).hasSize(2);
        assertThat(result.getPetBreedBreakdown()).hasSize(1);
        assertThat(result.getPetBreedBreakdown().get(0).getSpecies()).isEqualTo("DOG");
        assertThat(result.getPetBreedBreakdown().get(0).getBreed()).isEqualTo("골든 리트리버");
        assertThat(result.getAvgPetAge()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("Mapper 8개 메서드 모두 각 1회씩 호출")
    void getVisitStats_callsAllMapperMethodsOnce() {
        stubAllMapperMethods(0, 0);

        dashboardService.getVisitStats(FAIR_ID);

        then(dashboardMapper).should(times(1)).selectTotalVisitors(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectTotalConfirmedReservations(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectChannelBreakdown(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectGenderBreakdown(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectAgeGroupBreakdown(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectPetSpeciesBreakdown(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectPetBreedBreakdown(FAIR_ID);
        then(dashboardMapper).should(times(1)).selectAvgPetAge(FAIR_ID);
    }

    @Test
    @DisplayName("방문 통계 - 담당 행사가 아닌 EVENT_ADMIN이면 ACCESS_DENIED를 던지고 Mapper를 호출하지 않는다 (엑셀 내보내기도 이 메서드를 거치므로 함께 차단됨)")
    void getVisitStats_담당행사아니면_예외를_던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertAccessDenied(() -> dashboardService.getVisitStats(FAIR_ID));
        then(dashboardMapper).should(never()).selectTotalVisitors(any());
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────────────

    private HourlyEntryTrendDto makeHourlyDto(int hour, int count) {
        HourlyEntryTrendDto dto = new HourlyEntryTrendDto();
        dto.setEntryHour(hour);
        dto.setEntryCount(count);
        return dto;
    }

    private BoothVisitStatDto makeBoothDto(Long boothId, String number, int visitorCount) {
        BoothVisitStatDto dto = new BoothVisitStatDto();
        dto.setBoothId(boothId);
        dto.setBoothNumber(number);
        dto.setUniqueVisitorCount(visitorCount);
        return dto;
    }

    private void stubAllMapperMethods(int visitors, int confirmed) {
        given(dashboardMapper.selectTotalVisitors(FAIR_ID)).willReturn(visitors);
        given(dashboardMapper.selectTotalConfirmedReservations(FAIR_ID)).willReturn(confirmed);
        given(dashboardMapper.selectChannelBreakdown(FAIR_ID)).willReturn(List.of());
        given(dashboardMapper.selectGenderBreakdown(FAIR_ID)).willReturn(List.of());
        given(dashboardMapper.selectAgeGroupBreakdown(FAIR_ID)).willReturn(List.of());
        given(dashboardMapper.selectPetSpeciesBreakdown(FAIR_ID)).willReturn(List.of());
        given(dashboardMapper.selectPetBreedBreakdown(FAIR_ID)).willReturn(List.of());
        given(dashboardMapper.selectAvgPetAge(FAIR_ID)).willReturn(null);
    }

    private LabelCountDto makeLabelCount(String label, int count) {
        LabelCountDto dto = new LabelCountDto();
        dto.setLabel(label);
        dto.setCount(count);
        return dto;
    }

    private PetBreedStatDto makePetBreed(String species, String breed, int count) {
        PetBreedStatDto dto = new PetBreedStatDto();
        dto.setSpecies(species);
        dto.setBreed(breed);
        dto.setCount(count);
        return dto;
    }
}
