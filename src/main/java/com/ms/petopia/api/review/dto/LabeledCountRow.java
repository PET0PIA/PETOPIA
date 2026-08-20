package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "GROUP BY 한 컬럼, COUNT(*)" 형태의 매퍼 조회 결과를 담는 범용 row. 동반유형·방문목적
 * 분포처럼 컬럼만 다르고 모양이 같은 집계 쿼리 여러 개에서 재사용한다.
 */
@Getter
@Setter
public class LabeledCountRow {
    private String label;
    private long count;
}
