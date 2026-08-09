package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 공개 행사 목록(지난 행사/예정 행사/티켓 예매 가능한 행사) 한 줄. 카드 형태로 훑어보는
 * 용도라 {@link FairPublicSummaryResponse}보다 필드를 더 줄였다(description/noticeText/
 * address/indoorOutdoor/status 제외) - 상세 내용은 목록에서 눌러 들어간 뒤
 * {@code GET /api/fairs/{fairId}/public}로 따로 조회한다.
 */
public record FairPublicListItemResponse(
        Long fairId,
        String name,
        String category,
        String posterImageUrl,
        String placeName,
        LocalDate operationStartDate,
        LocalDate operationEndDate
) {
}
