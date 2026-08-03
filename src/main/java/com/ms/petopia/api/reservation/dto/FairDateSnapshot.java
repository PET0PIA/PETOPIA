package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FairDateSnapshot {
    private Long fairDateId;
    private Long fairId;
    private LocalDate operationDate;
}
