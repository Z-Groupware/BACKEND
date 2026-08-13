package com.module06.backend.action.presentation.api;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.ErrorResponse;

/*
 * 2026-08-13 — 메서드 보안에서 거절된 회사 전체 액션 조회 요청을 AC-015로 변환한다. 공통
 * GlobalExceptionHandler는 AuthorizationDeniedException을 개별로 안 잡아 500으로 처리하므로
 * (meetingroom.MeetingRoomSecurityExceptionHandler와 동일 문제·동일 해법), 이 컨트롤러에만
 * 우선 적용한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CompanyActionController.class)
public class CompanyActionSecurityExceptionHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(ActionErrorCode.ACTION_COMPANY_VIEW_FORBIDDEN.getHttpStatus())
                .body(ErrorResponse.of(
                        ActionErrorCode.ACTION_COMPANY_VIEW_FORBIDDEN,
                        request.getRequestURI(),
                        MDC.get(TRACE_ID_MDC_KEY)
                ));
    }
}
