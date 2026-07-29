package com.ms.petopia.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 에러 응답 바디.
 *
 * <pre>
 * {
 *   "code": "U001",
 *   "message": "존재하지 않는 사용자입니다.",
 *   "status": 404,
 *   "path": "/api/users/1",
 *   "timestamp": "2026-07-29T10:00:00",
 *   "fieldErrors": [ { "field": "email", "rejectedValue": "abc", "reason": "형식이 올바르지 않습니다." } ]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        int status,
        String path,
        LocalDateTime timestamp,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String rejectedValue, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(
                errorCode.getCode(),
                message,
                errorCode.getHttpStatus().value(),
                path,
                LocalDateTime.now(),
                List.of()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                errorCode.getCode(),
                message,
                errorCode.getHttpStatus().value(),
                path,
                LocalDateTime.now(),
                fieldErrors
        );
    }
}
