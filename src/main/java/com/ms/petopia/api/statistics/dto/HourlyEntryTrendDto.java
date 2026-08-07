package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HourlyEntryTrendDto {
    private int entryHour;   // 0~23
    private int entryCount;
}
