package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class QrIssuanceSummaryDto {
    private Long fairDateId;
    private LocalDate operationDate;
    private int qrIssuedCount;
    private int qrActiveCount;
    private int qrRevokedCount;
}
