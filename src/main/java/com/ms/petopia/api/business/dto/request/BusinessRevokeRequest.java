package com.ms.petopia.api.business.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 승인된 사업자를 나중에 취소 처리할 때(예: 조작 서류 발각) 요청 (PATCH /api/businesses/{id}/revoke)
@Getter
@Setter
public class BusinessRevokeRequest {

    // 빈 값 체크는 서비스에서 직접(구체적인 에러코드 반환 위해). 길이만 여기서 방어(DB 컬럼 varchar(500))
    @Size(max = 500, message = "취소 사유는 500자 이하여야 합니다.")
    private String revokeReason;

}
