package com.ms.petopia.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 운영 문구 한 건(인사말, 접수 안내 등). */
public record ChatSetting(
        @NotBlank
        @Size(max = 50)
        String settingKey,

        @NotBlank(message = "문구를 입력해주세요.")
        @Size(max = 2000)
        String settingValue,

        String description
) {
}
