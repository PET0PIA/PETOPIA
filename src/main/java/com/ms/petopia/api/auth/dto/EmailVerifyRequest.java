package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerifyRequest(
        @NotBlank String token
) {
}
