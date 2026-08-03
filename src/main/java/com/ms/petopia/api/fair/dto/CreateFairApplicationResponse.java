package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 행사 신청 등록 결과.
 *
 * @param fairId    발급된 신청서(=행사) PK
 * @param name      행사명
 * @param status    항상 RECEIVED(신청 접수됨)
 * @param createdAt 신청서 제출 일시
 */
public record CreateFairApplicationResponse(
        Long fairId,
        String name,
        String status,
        LocalDateTime createdAt
) {
}
