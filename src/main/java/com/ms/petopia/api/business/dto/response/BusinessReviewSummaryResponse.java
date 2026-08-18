package com.ms.petopia.api.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// GET /api/businesses/review 응답 항목 하나 - 심사 목록용 요약
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessReviewSummaryResponse {

    private Long businessId;
    private String name;
    private String ceoName;
    private String bizRegNo;
    private String verifyStatus;
    private LocalDateTime createdAt;

}
