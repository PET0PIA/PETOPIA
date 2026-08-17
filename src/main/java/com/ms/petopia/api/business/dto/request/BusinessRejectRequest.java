package com.ms.petopia.api.business.dto.request;

import lombok.Getter;
import lombok.Setter;

// 사업자 심사 반려 요청 (PATCH /api/businesses/{id}/reject)
@Getter
@Setter
public class BusinessRejectRequest {

    private String rejectReason; // 반려 사유. 공백 여부는 서비스에서 직접 체크(구체적 에러코드 반환용)

}
