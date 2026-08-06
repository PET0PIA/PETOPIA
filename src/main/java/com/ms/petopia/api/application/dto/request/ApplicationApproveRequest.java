package com.ms.petopia.api.application.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

// PUT /api/applications/{applicationId}/approve 요청 (행사 담당자용)
@Getter
@Setter
public class ApplicationApproveRequest {

    // 0 이상만 허용(음수 방지). null이면 검증 안 하고 통과 -> 슬롯 가격 합계로 자동 계산됨
    @PositiveOrZero
    private Long finalPrice; // 최종 가격, null이면 슬롯 가격 합계로 자동 계산

}
