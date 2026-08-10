package com.module06.backend.notice.presentation.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.notice.exception.NoticeErrorCode;

/* 공지 요청 본문 검증 실패를 NT-003과 필드별 details로 변환하는 전용 예외 처리기다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = NoticeController.class)
public class NoticeValidationExceptionHandler {

    /* Bean Validation의 필드 오류를 공지 명세의 NT-003 응답으로 변환한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        /* 검증에 실패한 각 필드명과 기본 사유를 ErrorResponse details로 변환한다. */
        List<ErrorResponse.FieldErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();

        /* 공지 전용 입력 코드와 현재 traceId를 사용해 400 응답을 반환한다. */
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                NoticeErrorCode.INVALID_NOTICE_INPUT,
                request.getRequestURI(),
                MDC.get("traceId"),
                details
        ));
    }
}
