package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 관리자 취소 신청 큐 목록 한 줄({@code GET /api/fair-cancel-requests}). 특정 행사에 갇힌
 * {@link FairCancelRequestResponse}와 달리 fairName을 함께 담아, 전체 행사를 가로질러 훑어봐도
 * 어느 행사의 취소 신청인지 바로 알 수 있게 한다.
 */
public record FairCancelRequestQueueItemResponse(
        Long fairCancelRequestId,
        Long fairId,
        String fairName,
        Long requestedBy,
        String reason,
        String status,
        LocalDateTime createdAt
) {
}
