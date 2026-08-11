package com.ms.petopia.api.fair.dto;

/**
 * 공개 행사 목록 조회 필터. operation_end_date를 오늘과 비교해서 나눈다
 * ({@code FairService#listPublicFairs} 참고) - operation_end_date가 없는(일정 미정) 행사는
 * 아직 끝났다고 볼 근거가 없으니 UPCOMING으로 취급한다.
 */
public enum PublicFairListFilter {
    UPCOMING,
    PAST
}
