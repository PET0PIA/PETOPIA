package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 공개 행사 목록(지난 행사/예정 행사/티켓 예약 가능한 행사) 한 줄. 카드 형태로 훑어보는
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
        /** 사전예약 가능 여부(예약 기간 안 + 정원 남은 미래 운영일 존재). 목록 카드의 "사전예약중" 배지에 쓴다. */
        boolean reservable,
        /** 참가업체 부스 모집중 여부(모집공고 마감 전 + 행사 종료 아님 + 빈 슬롯). "참가업체 모집중" 배지에 쓴다. */
        boolean recruiting,
        /**
         * {@code fairs.status} 그대로({@link FairStatus} 이름). 목록 카드의 상태 문구를 가르는 데 쓴다 -
         * reservable만으로는 "아직 예약 전"과 "이미 진행 중"을 구분할 수 없어 진행 중인 행사가
         * "오픈 예정"으로 표시되던 문제가 있었다. 상태 판정은 서버 시계(FairTransitionService가
         * 갱신하는 status)를 따라야 정확하므로, 화면에서 오늘 날짜와 비교하지 않고 이 값을 내려준다.
         */
        String status
) {
}
