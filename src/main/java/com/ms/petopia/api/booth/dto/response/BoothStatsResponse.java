package com.ms.petopia.api.booth.dto.response;

import com.ms.petopia.api.statistics.dto.LabelCountDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 부스 관리자용 방문 통계 응답("내 부스 관리" 상세 - 부스 QR 스캔·리뷰·찜 전환율). */
@Getter
@Builder
public class BoothStatsResponse {

    private Long boothId;
    private int uniqueVisitorCount;
    private long totalScanCount;
    private int revisitCount;
    private double revisitRate;
    private int reviewedVisitorCount;
    private double reviewConversionRate;
    private int favoritedVisitorCount;
    private double favoriteConversionRate;
    private List<BoothDailyVisitResponse> dailyVisits;
    private List<LabelCountDto> petSpeciesBreakdown;
    private Double avgPetAge;
    private List<LabelCountDto> petAllergyBreakdown;

    public static BoothStatsResponse from(
            Long boothId, BoothStatsSummaryRow summary, List<BoothDailyVisitRow> dailyVisits,
            List<LabelCountDto> petSpeciesBreakdown, Double avgPetAge, List<LabelCountDto> petAllergyBreakdown
    ) {

        int visitors = summary.getUniqueVisitorCount();

        return BoothStatsResponse.builder()
                .boothId(boothId)
                .uniqueVisitorCount(visitors)
                .totalScanCount(summary.getTotalScanCount())
                .revisitCount(summary.getRevisitCount())
                .revisitRate(rate(summary.getRevisitCount(), visitors))
                .reviewedVisitorCount(summary.getReviewedVisitorCount())
                .reviewConversionRate(rate(summary.getReviewedVisitorCount(), visitors))
                .favoritedVisitorCount(summary.getFavoritedVisitorCount())
                .favoriteConversionRate(rate(summary.getFavoritedVisitorCount(), visitors))
                .dailyVisits(dailyVisits.stream().map(BoothDailyVisitResponse::from).toList())
                .petSpeciesBreakdown(petSpeciesBreakdown)
                .avgPetAge(avgPetAge)
                .petAllergyBreakdown(petAllergyBreakdown)
                .build();

    }

    // 방문자가 0명이면 분모가 0이라 나눗셈 자체가 무의미하므로 0.0으로 고정한다. 소수 첫째자리까지.
    private static double rate(int part, int total) {

        if (total == 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / total) / 10.0;

    }

}
