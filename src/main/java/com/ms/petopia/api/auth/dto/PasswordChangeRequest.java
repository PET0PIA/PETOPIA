package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$",
                message = "비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상이어야 합니다."
        )
        String newPassword
) {
}
