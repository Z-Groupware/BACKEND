package com.module06.backend.global.exception;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI(), currentTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                     HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId(), details));
    }

    /**
     * 컨트롤러의 {@code @RequestParam}·{@code @PathVariable}에 붙은 {@code @Min}·{@code @Max} 같은
     * 제약이 깨졌을 때(클래스에 {@code @Validated} 필요). {@code @RequestBody} 검증 실패는
     * {@link MethodArgumentNotValidException} 이 따로 잡는다 — 이건 그 밖의 메서드 파라미터용이다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    /** AOP 기반 메서드 검증({@code MethodValidationPostProcessor})을 쓰는 곳이 생기면 여기로 온다. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                          HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
                                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    /**
     * 마지막 그물. 응답 본문에는 traceId 만 나가고 원인은 숨기지만(정보 노출 방지), 서버 로그에는
     * 반드시 스택트레이스를 남긴다 — 남기지 않으면 Z-003 이 나간 뒤 원인을 되짚을 방법이 아예
     * 없어진다(응답의 traceId 로 대조할 로그 라인 자체가 없다).
     *
     * <p>traceId 를 메시지에 직접 박는다. logback 설정 파일이 없어 Spring 기본 패턴을 쓰는데,
     * 그 패턴에는 MDC 가 들어 있지 않아 {@code %X{traceId}} 로는 찍히지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외 — traceId={} {} {}",
                currentTraceId(), request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI(), currentTraceId()));
    }

    private String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }
}
