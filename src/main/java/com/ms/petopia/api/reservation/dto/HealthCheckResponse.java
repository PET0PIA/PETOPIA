package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 연결 점검 응답.
 *
 * @param requestId  HttpLoggingFilter가 발급한 요청 ID. 응답을 받은 뒤 로그를 찾아갈 때 쓴다.
 * @param up         모든 구성요소가 정상이면 true
 * @param components 구성요소별 점검 결과
 */
public record HealthCheckResponse(
        String requestId,
        boolean up,
        List<ComponentStatus> components,
        LocalDateTime checkedAt
) {

    public static HealthCheckResponse of(String requestId, List<ComponentStatus> components) {
        boolean up = components.stream().allMatch(ComponentStatus::up);
        return new HealthCheckResponse(requestId, up, components, LocalDateTime.now());
    }
}
