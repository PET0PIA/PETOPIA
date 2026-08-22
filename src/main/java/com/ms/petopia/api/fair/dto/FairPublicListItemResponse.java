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
        LocalDate operationEndDate,
        /** 반려동물 동반 가능 여부. 목록 카드의 "반려동물 동반" 배지에 쓴다. */
        boolean petAllowed,
        /** 사전예약 가능 여부(예매 기간 안 + 정원 남은 미래 운영일 존재). 목록 카드의 "사전예약중" 배지에 쓴다. */
        boolean reservable,
        /** 참가기업 부스 모집중 여부(모집공고 마감 전 + 행사 종료 아님 + 빈 슬롯). "참가기업 모집중" 배지에 쓴다. */
        boolean recruiting
) {
}
