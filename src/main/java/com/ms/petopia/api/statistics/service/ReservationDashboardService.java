package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.BoothVisitStatDto;
import com.ms.petopia.api.statistics.dto.HourlyEntryTrendDto;
import com.ms.petopia.api.statistics.dto.QrIssuanceSummaryDto;
import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
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

    @Transactional(readOnly = true)
    public List<ReservationDateSummaryDto> getDateSummary(Long fairId, LocalDate date) {
        return dashboardMapper.selectDateSummaryList(fairId, date);
    }

    @Transactional(readOnly = true)
    public List<QrIssuanceSummaryDto> getQrIssuanceSummary(Long fairId) {
        return dashboardMapper.selectQrIssuanceSummary(fairId);
    }

    @Transactional(readOnly = true)
    public List<HourlyEntryTrendDto> getHourlyEntryTrend(Long fairId, LocalDate date) {
        return dashboardMapper.selectHourlyEntryTrend(fairId, date);
    }

    @Transactional(readOnly = true)
    public List<BoothVisitStatDto> getBoothVisitStats(Long fairId) {
        return dashboardMapper.selectBoothVisitStats(fairId);
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

}
