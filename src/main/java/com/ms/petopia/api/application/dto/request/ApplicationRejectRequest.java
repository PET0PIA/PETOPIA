package com.ms.petopia.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// PUT /api/applications/{applicationId}/reject 요청 (행사 담당자용)
@Getter
@Setter
public class ApplicationRejectRequest {

    @NotBlank
    private String rejectReason; // 반려 이유

}
