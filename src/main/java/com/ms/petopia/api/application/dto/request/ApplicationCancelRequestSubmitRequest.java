package com.ms.petopia.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// POST /api/applications/{applicationId}/cancel-requests 요청 (사업자용)
@Getter
@Setter
public class ApplicationCancelRequestSubmitRequest {

    @NotBlank(message = "취소 사유를 입력해야 합니다.")
    @Size(max = 500, message = "취소 사유는 500자 이하여야 합니다.")
    private String reason;

}
