package com.ms.petopia.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 발생하는 예외.
 *
 * <p>{@link ErrorCode}의 기본 메세지를 그대로 쓰거나, 상황에 맞는 상세 메세지로 덮어쓸 수 있다.
 */
@Getter
public class CommonException extends RuntimeException {

    private final ErrorCode errorCode;

    public CommonException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CommonException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CommonException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public CommonException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
