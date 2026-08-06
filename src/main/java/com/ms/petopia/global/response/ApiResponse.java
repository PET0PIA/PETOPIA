package com.ms.petopia.global.response;

import org.springframework.http.HttpStatus;

public record ApiResponse<T>(
        boolean success,
        int status,
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(HttpStatus httpStatus, T data) {
        return new ApiResponse<>(true, httpStatus.value(), "SUCCESS", null, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, HttpStatus.OK.value(), "SUCCESS", null, data);
    }


}
