package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FairSummaryDto {
    private Long fairId;
    private String fairName;
    private String status;
    private LocalDate operationStartDate;
    private LocalDate operationEndDate;
    private int totalReservations;
    private int totalVisitors;
}
