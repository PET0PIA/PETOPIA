package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

/** review_id별 선택한 태그 라벨 1건. 목록 조회에서 review_id로 그룹핑해 붙인다. */
@Getter
@Setter
public class ReviewTagLabelRow {
    private Long reviewId;
    private String label;
}
