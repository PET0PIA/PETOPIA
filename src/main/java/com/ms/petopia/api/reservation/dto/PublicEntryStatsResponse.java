package com.ms.petopia.api.reservation.dto;

/**
 * 홈 화면에 보여줄 공개 입장 요약. 합계 숫자 두 개뿐이라 개인정보가 섞이지 않는다
 * (누가 언제 왔는지는 담지 않는다) - 그래서 로그인 없이 열어줄 수 있다.
 *
 * @param visitorCount 한 번이라도 행사에 입장한 사용자 수(중복 제거)
 * @param petCount     그 입장에 함께 온 반려동물 수(중복 제거)
 */
public record PublicEntryStatsResponse(
        long visitorCount,
        long petCount
) {
}
