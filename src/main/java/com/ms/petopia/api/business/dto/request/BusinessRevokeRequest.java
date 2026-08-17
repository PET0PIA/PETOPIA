package com.ms.petopia.api.business.dto.request;

import lombok.Getter;
import lombok.Setter;

// 승인된 사업자를 나중에 취소 처리할 때(예: 조작 서류 발각) 요청 (PATCH /api/businesses/{id}/revoke)
@Getter
@Setter
public class BusinessRevokeRequest {

    private String revokeReason; // 취소 사유. 공백 여부는 서비스에서 직접 체크

}
