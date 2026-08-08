package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoothVisitStatDto {
    private Long boothId;
    private String boothNumber;
    private String boothName;
    private int uniqueVisitorCount;
    private int totalScanCount;
}
