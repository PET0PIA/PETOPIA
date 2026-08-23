package com.ms.petopia.api.booth.mapper;

import com.ms.petopia.api.booth.dto.response.BoothDailyVisitRow;
import com.ms.petopia.api.booth.dto.response.BoothStatsSummaryRow;
import com.ms.petopia.api.statistics.dto.LabelCountDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 부스 관리자용 방문 통계 조회 매퍼. booth_visits를 사용자 단위로 먼저 집계한 뒤
 * booth_feedbacks(리뷰)·booth_favorites(찜)와 LEFT JOIN해서 전환율까지 한 번에 계산한다.
 * XML은 {@code mapper/booth/BoothStatsMapper.xml}에 있다.
 */
@Mapper
public interface BoothStatsMapper {

    /** 방문자 수·스캔 수·재방문/리뷰/찜 전환 인원을 한 행으로 요약 조회한다. 방문 기록이 없어도
     * 값이 전부 0인 행이 온다(집계 쿼리라 결과가 비지 않음). */
    BoothStatsSummaryRow selectSummary(@Param("boothId") Long boothId);

    /** 최초 방문일(first_visited_at의 날짜) 기준 일자별 고유 방문자 수. 오래된 날짜부터 정렬. */
    List<BoothDailyVisitRow> selectDailyVisitCounts(@Param("boothId") Long boothId);

    /**
     * 방문자가 예약 시 등록한 반려동물 종 분포(reservation_pets 스냅샷 기준). 반려동물 정보가
     * 없는 방문(현장예매 등)은 "미등록"으로 잡힌다. ReservationDashboardMapper의
     * selectPetBreedBreakdown과 같은 패턴을 booth_visits 기준으로 좁힌 것.
     */
    List<LabelCountDto> selectPetSpeciesBreakdown(@Param("boothId") Long boothId);

    /** 평균 반려동물 나이(최초 방문 시점 기준). 반려동물 등록이 하나도 없으면 null. */
    Double selectAvgPetAge(@Param("boothId") Long boothId);

    /** 반려동물 알레르기 분포. 알레르기가 없는 반려동물은 집계에서 빠진다(INNER JOIN). */
    List<LabelCountDto> selectPetAllergyBreakdown(@Param("boothId") Long boothId);
}
