package com.ms.petopia.api.reservation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 개별 구성요소(DB, Redis)의 점검 결과.
 *
 * <pre>
 * {
 *   "name": "mysql",
 *   "up": true,
 *   "latencyMs": 4,
 *   "message": "SELECT 1 FROM DUAL 성공",
 *   "details": { "dbName": "petopia_db", "connectionId": 42 }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComponentStatus(
        String name,
        boolean up,
        long latencyMs,
        String message,
        Map<String, Object> details
) {

    public static ComponentStatus up(String name, long latencyMs, String message, Map<String, Object> details) {
        return new ComponentStatus(name, true, latencyMs, message, details);
    }

    /**
     * 실패 응답에는 예외 클래스명과 메세지만 담는다.
     * 스택트레이스는 로그에만 남기고 응답으로는 내보내지 않는다.
     */
    public static ComponentStatus down(String name, long latencyMs, Throwable cause) {
        return new ComponentStatus(
                name,
                false,
                latencyMs,
                cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                Map.of()
        );
    }
}
