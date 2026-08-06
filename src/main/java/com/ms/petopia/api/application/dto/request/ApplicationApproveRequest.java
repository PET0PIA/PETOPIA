package com.ms.petopia.api.application.dto.request;

import lombok.Getter;
import lombok.Setter;

// PUT /api/applications/{applicationId}/approve 요청 (행사 담당자용)
@Getter
@Setter
public class ApplicationApproveRequest {

    private Long finalPrice; // 최종 가격, null이면 슬롯 가격 합계로 자동 계산

}
