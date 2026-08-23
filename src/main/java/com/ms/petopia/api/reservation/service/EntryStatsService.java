package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.PublicEntryStatsResponse;
import com.ms.petopia.api.reservation.dto.PublicEntryStatsRow;
import com.ms.petopia.api.reservation.mapper.EntryStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntryStatsService {

    private final EntryStatsMapper entryStatsMapper;

    /**
     * 홈 화면에 보여줄 서비스 전체 입장 요약.
     *
     * <p>입장 기록이 하나도 없으면 서브쿼리 두 개가 모두 0을 내주므로 row 자체는 항상 온다.
     * 그래도 null을 대비해 두는 이유: 이 값이 없다고 홈이 500으로 죽으면 안 되고, 0은
     * "아직 방문 기록이 없다"는 정상 상태를 정확히 표현한다.
     */
    @Transactional(readOnly = true)
    public PublicEntryStatsResponse getPublicStats() {
        PublicEntryStatsRow row = entryStatsMapper.selectPublicEntryStats();
        if (row == null) {
            return new PublicEntryStatsResponse(0L, 0L);
        }
        return new PublicEntryStatsResponse(row.getVisitorCount(), row.getPetCount());
    }
}
