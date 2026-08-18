package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.statistics.dto.*;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent;
import com.ms.petopia.api.statistics.mapper.ReservationDashboardMapper;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationDashboardService {
    private final ReservationDashboardMapper dashboardMapper;
    private final DashboardEmitterRegistry emitterRegistry;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    /**
     * SecurityConfig는 EVENT_ADMIN/SUPER_ADMIN role인 것까지만 걸러주고 "이 행사" 담당자인지는
     * 못 가리므로 여기서 한 번 더 확인한다({@link FairAdminAccessGuard} 참고). 단, 이 메서드는
     * {@link #onReservationStatusChanged}(SecurityContext 없는 @Async 리스너)에서도 재사용되므로
     * 여기엔 가드를 넣지 않고, HTTP 요청 경로인 컨트롤러에서 별도로 checkAssigned를 호출한다.
     */
    @Transactional(readOnly = true)
    public List<ReservationDateSummaryDto> getDateSummary(Long fairId, LocalDate date) {
        return dashboardMapper.selectDateSummaryList(fairId, date);
    }

    @Transactional(readOnly = true)
    public List<QrIssuanceSummaryDto> getQrIssuanceSummary(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        return dashboardMapper.selectQrIssuanceSummary(fairId);
    }

    @Transactional(readOnly = true)
    public List<HourlyEntryTrendDto> getHourlyEntryTrend(Long fairId, LocalDate date) {
        fairAdminAccessGuard.checkAssigned(fairId);
        return dashboardMapper.selectHourlyEntryTrend(fairId, date);
    }

    @Transactional(readOnly = true)
    public List<BoothVisitStatDto> getBoothVisitStats(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        return dashboardMapper.selectBoothVisitStats(fairId);
    }

    @Transactional(readOnly = true)
    public List<LabelCountDto> getBoothVisitPatternDistribution(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        return dashboardMapper.selectBoothVisitPatternDistribution(fairId);
    }

    @Async // 별도 스레드에서 실행. 예약처리 흐름을 블로킹X
    @TransactionalEventListener
    public void onReservationStatusChanged(ReservationStatusChangedEvent event){
        Long fairId = event.fairId();
        List<SseEmitter> targets = emitterRegistry.getEmitters(fairId);
        if(targets.isEmpty()) return;; // 연결된 관리자가 없으면 조회x

        // 트랜잭션이 커밋된 후이므로 새 트랜잭션으로 최신 전체 날짜 현황 조회
        List<ReservationDateSummaryDto> latest = getDateSummary(fairId, null);
        for(SseEmitter emitter : targets){
            try {
                emitter.send(SseEmitter.event()
                        .name("dashboard-update") // 프론트 EventSource 연결
                        .data(latest, MediaType.APPLICATION_JSON)
                );
            } catch (IOException e) {
                // 전송 실패한 emitter는 onError 콜백이 registry에서 자동 제거
                log.debug("SSE push 실패 - fairId={}", fairId);
            }
        }
    }

    @Transactional(readOnly = true)
    public VisitStatsDto getVisitStats(Long fairId){
        fairAdminAccessGuard.checkAssigned(fairId);
        int totalVisitors = dashboardMapper.selectTotalVisitors(fairId);
        int totalConfirmed = dashboardMapper.selectTotalConfirmedReservations(fairId);

        // 확정 예약이 하나도 없으면 0.0, 있으면 소수점 1자리 반올림
        double visitRate = totalConfirmed > 0 ? Math.round((double) totalVisitors / totalConfirmed * 1000.0) / 10.0 : 0.0;
        VisitStatsDto dto = new VisitStatsDto();
        dto.setTotalVisitors(totalVisitors);
        dto.setTotalConfirmedReservations(totalConfirmed);
        dto.setVisitRate(visitRate);
        dto.setChannelBreakdown(dashboardMapper.selectChannelBreakdown(fairId));
        dto.setGenderBreakdown(dashboardMapper.selectGenderBreakdown(fairId));
        dto.setAgeGroupBreakdown(dashboardMapper.selectAgeGroupBreakdown(fairId));
        dto.setPetSpeciesBreakdown(dashboardMapper.selectPetSpeciesBreakdown(fairId));
        dto.setPetBreedBreakdown(dashboardMapper.selectPetBreedBreakdown(fairId));
        dto.setAvgPetAge(dashboardMapper.selectAvgPetAge(fairId));
        dto.setAvgBoothsPerVisitor(dashboardMapper.selectAvgBoothsPerVisitor(fairId));
        return dto;
    }

}
