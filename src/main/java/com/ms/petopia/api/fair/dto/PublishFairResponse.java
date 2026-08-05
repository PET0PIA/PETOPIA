package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 행사 공개 처리 결과.
 *
 * @param fairId      공개한 행사 PK
 * @param status      공개 시점의 fairs.status (공개는 status를 바꾸지 않는다)
 * @param publishedAt 공개 일시. 이미 공개된 행사를 다시 호출한 경우 최초 공개 일시를 그대로 반환한다
 */
public record PublishFairResponse(
        Long fairId,
        String status,
        LocalDateTime publishedAt
) {
}
