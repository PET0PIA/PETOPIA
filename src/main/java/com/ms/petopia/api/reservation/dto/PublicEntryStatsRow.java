package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 서비스 전체 입장 집계 한 줄(MyBatis 조회 결과).
 *
 * <p>둘 다 중복을 제거한 "고유" 수다. 즉 같은 사람이 행사 세 곳에 다녀오면 1명으로 센다.
 * 관리자 대시보드({@code /api/admin/dashboard})의 totalVisitors는 행사별 방문자를 더한
 * 누적 값이라 이 숫자보다 크게 나온다 - 두 화면의 숫자가 다른 건 버그가 아니라 정의 차이다.
 */
@Getter
@Setter
public class PublicEntryStatsRow {
    private long visitorCount;
    private long petCount;
}
