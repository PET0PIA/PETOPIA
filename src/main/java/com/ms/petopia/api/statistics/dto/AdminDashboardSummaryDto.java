package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminDashboardSummaryDto {
    private int totalFairs; // 전체운영 행사 수
    private int inProgressFairs; // 진행중
    private int preparingFairs; // 준비중
    private int endedFairs; // 종료
    private int totalReservations; // 전체 확정 예약 수
    private int totalVisitors; // 전체 방문자 수
}
