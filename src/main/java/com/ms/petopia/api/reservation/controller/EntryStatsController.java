package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.PublicEntryStatsResponse;
import com.ms.petopia.api.reservation.service.EntryStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입장 기록 기반 공개 통계. 홈 화면 상단의 "방문한 사용자 / 함께한 반려동물" 숫자를 준다.
 *
 * <p>행사별 방문 통계({@code /api/fairs/{fairId}/visit-stats}, 통계 도메인)와는 다른 API다.
 * 그쪽은 담당 관리자만 볼 수 있는 행사 단위 상세 집계(성별·연령대·부스 분포까지)이고,
 * 이쪽은 누구나 볼 수 있는 서비스 전체 합계 숫자 두 개뿐이다.
 *
 * <p>입장 기록(entry_records)과 동반 반려동물(reservation_pets)은 예약·입장 도메인의
 * 테이블이라 이 도메인에 둔다.
 */
@RestController
@RequestMapping("/api/v1/entry-stats")
@RequiredArgsConstructor
public class EntryStatsController {

    private final EntryStatsService entryStatsService;

    /** 로그인 없이 호출할 수 있다(합계 숫자만 나가고 개인정보는 담기지 않는다). */
    @GetMapping("/public")
    public PublicEntryStatsResponse getPublicStats() {
        return entryStatsService.getPublicStats();
    }
}
