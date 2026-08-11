package com.ms.petopia.api.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserMeResponse(
        Long userId,
        String email,
        String nickname,
        LocalDate birthDate,
        String phone,
        String gender,
        String address,
        String role,
        String status,
        boolean emailVerified,
        LocalDateTime createdAt
) {
}
