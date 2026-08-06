package com.ms.petopia.api.fair.dto;

/**
 * 행사 취소 신청 등록 요청. fairId는 요청 본문이 아니라 경로에서 받는다.
 */
public record CreateFairCancelRequestRequest(
        String reason
) {
}
