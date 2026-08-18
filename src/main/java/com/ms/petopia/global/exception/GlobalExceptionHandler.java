package com.ms.petopia.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직에서 의도적으로 던진 예외.
     */
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ErrorResponse> handleCommonException(CommonException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[CommonException] code={}, message={}, uri={}",
                errorCode.getCode(), e.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage(), request.getRequestURI()));
    }

    /**
     * @Valid 바인딩 실패.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldError(
                        fieldError.getField(),
                        String.valueOf(fieldError.getRejectedValue()),
                        fieldError.getDefaultMessage()))
                .toList();

        log.warn("[MethodArgumentNotValidException] uri={}, fieldErrors={}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI(), fieldErrors));
    }

    /**
     * 필수 헤더·요청 파라미터 누락 등 요청 바인딩 실패.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleServletRequestBinding(
            ServletRequestBindingException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        log.warn("[ServletRequestBindingException] uri={}, message={}", request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }

    /** 쿼리 파라미터·경로 변수 타입 변환 실패 (예: 잘못된 날짜 형식). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        log.warn("[MethodArgumentTypeMismatchException] uri={}, param={}, value={}",
                request.getRequestURI(), e.getName(), e.getValue());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }

    /** JSON 형식 오류 또는 enum 바인딩 실패. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        log.warn("[HttpMessageNotReadableException] uri={}", request.getRequestURI());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }

    /**
     * 지원하지 않는 HTTP 메서드.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        log.warn("[HttpRequestMethodNotSupportedException] uri={}, method={}", request.getRequestURI(), e.getMethod());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }

    /**
     * 매핑되지 않은 경로 요청.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        log.warn("[NoResourceFoundException] uri={}", request.getRequestURI());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }

    /**
     * 처리하지 못한 나머지 예외. 내부 메세지는 노출하지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("[Exception] uri={}", request.getRequestURI(), e);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }
}
