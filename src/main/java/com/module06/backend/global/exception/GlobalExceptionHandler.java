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

import com.module06.backend.global.audit.AuthzAuditLogger;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        // 인가 거부(403)만 감사 기록으로 남긴다. 이 프로젝트의 교차 회사 거부는 시큐리티 필터가 아니라
        // 서비스·컨트롤러의 도메인 규칙(MT_FORBIDDEN_SCOPE·HO_ACCESS_DENIED 등)에서 나오므로,
        // SecurityErrorResponder만 감시하면 정작 필요한 기록이 하나도 남지 않는다.
        // 404·400 등은 남기지 않는다 — 감사 대상이 아니고 로그만 불린다.
        if (errorCode.getHttpStatus() == HttpStatus.FORBIDDEN) {
            AuthzAuditLogger.deniedByDomain(request, errorCode.getCode());
        }
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI(), currentTraceId()));
    }

    private String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }
}
