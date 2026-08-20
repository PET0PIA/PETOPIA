package com.ms.petopia.api.review.dto;

/**
 * "값별 개수" 집계 한 줄. 동반유형·방문목적 분포(도넛) 등에 재사용한다.
 *
 * @param key   집계 기준값(예: companion_type의 "WITH_PET")
 * @param count 건수
 * @param ratio 전체 대비 비율(0~1). count가 0건이면 0.0
 */
public record CountItem(
        String key,
        long count,
        double ratio
) {
}
