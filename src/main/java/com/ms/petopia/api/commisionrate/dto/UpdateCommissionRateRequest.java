package com.ms.petopia.api.commisionrate.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateCommissionRateRequest(

        @NotNull CommissionRateScope scope,
        Long fairId, // scope=FAIR일 때만 필수 - 교차검증은 서비스 계층(CR001)
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal rate
) {
}
