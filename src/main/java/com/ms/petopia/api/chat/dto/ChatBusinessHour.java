package com.ms.petopia.api.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 요일별 상담 운영시간.
 *
 * @param dayOfWeek 1=월 ... 7=일. {@code java.time.DayOfWeek}와 같은 규칙이다 -
 *                  JS의 0=일 규칙과 섞이면 하루씩 밀린 채로 오래 안 들킨다.
 */
public record ChatBusinessHour(
        @NotNull
        @Min(1) @Max(7)
        Integer dayOfWeek,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime,

        Boolean isActive
) {
}
