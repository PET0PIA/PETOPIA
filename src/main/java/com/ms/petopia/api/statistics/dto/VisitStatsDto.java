package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class VisitStatsDto {
    private int totalVisitors; // 실제 입장한 총 방문자 수
    private int totalConfirmedReservations; // 확정된 예약 수
    private double visitRate; // 방문율 % (소수점1자리)
    private List<LabelCountDto> channelBreakdown; // 채널별(ONLINE/ONSITE_DIRECT)
    private List<LabelCountDto> genderBreakdown; // 성별
    private List<LabelCountDto> ageGroupBreakdown; // 연령대
    private List<LabelCountDto> petSpeciesBreakdown; // 반려동물 종
    private List<PetBreedStatDto> petBreedBreakdown; // 반려동물 품종
    private Double avgPetAge; // 평균 반려동물 나이, 데이터 없으면 null
    private Double avgBoothsPerVisitor; // 방문자 1명당 평균 방문 부스 수, 데이터 없으면 null
}
