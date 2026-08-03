package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OnsiteSalesPolicyRow {
    private Long onsiteSalesPolicyId;
    private Long fairDateId;
    private long price;
    private String status;
    private Long updatedBy;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
