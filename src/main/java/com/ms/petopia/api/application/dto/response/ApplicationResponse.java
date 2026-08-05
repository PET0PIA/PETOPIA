package com.ms.petopia.api.application.dto.response;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.domain.ApplicationForm;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

// 신청서 제출 응답
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private Long applicationId;
    private Long fairId;
    private Long businessId;
    private String status;
    private List<Long> boothSlotIds;
    private String purpose;
    private String itemsDesc;
    private String managerName;
    private String managerPhone;
    private String managerEmail;
    private String attachmentUrl;
    private LocalDateTime submittedAt;

    // Application + ApplicationForm + 슬롯ID목록을 응답 DTO로 변환
    public static ApplicationResponse from(Application application, ApplicationForm form, List<Long> boothSlotIds) {

        return ApplicationResponse.builder()
                .applicationId(application.getApplicationId())
                .fairId(application.getFairId())
                .businessId(application.getBusinessId())
                .status(application.getStatus().name())
                .boothSlotIds(boothSlotIds)
                .purpose(form.getPurpose())
                .itemsDesc(form.getItemsDesc())
                .managerName(form.getManagerName())
                .managerPhone(form.getManagerPhone())
                .managerEmail(form.getManagerEmail())
                .attachmentUrl(form.getAttachmentUrl())
                .submittedAt(application.getSubmittedAt())
                .build();

    }

}
