package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * booth_id로 그 부스가 속한 fair_id·business_id를 한 번에 조회하는 결과 row. booth 테이블은
 * business_id만 직접 들고 있고 fair_id는 application을 거쳐야 알 수 있어서(booth.application_id
 * -> application.fair_id), 부스 평가 제출 시 "이 부스가 지금 리뷰 중인 행사 소속이 맞는지"
 * 검증하고 fair_id/business_id를 채우는 데 쓴다.
 */
@Getter
@Setter
public class BoothFairBusinessRow {
    private Long boothId;
    private Long fairId;
    private Long businessId;
}
