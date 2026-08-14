package com.module06.backend.global.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.module06.backend.global.audit.AuthzAuditLogger;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.global.exception.ErrorCode;
import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;


@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final ObjectMapper objectMapper;


    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        AuthzAuditLogger.unauthenticated(request, AuthErrorCode.UNAUTHORIZED.getCode());
        write(request, response, AuthErrorCode.UNAUTHORIZED);
    }


    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        AuthzAuditLogger.deniedByFilter(request, CommonErrorCode.ACCESS_DENIED.getCode());
        write(request, response, CommonErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        // SSE 처럼 응답이 이미 시작된 뒤 거절되면 상태줄도 본문도 다시 쓸 수 없다.
        // 여기서 멈추지 않으면 "response is already committed" 2차 예외가 진짜 원인을 로그에서 덮는다.
        // 감사 로그는 호출부(commence·handle)에서 이미 남았으므로 거절 사실은 사라지지 않는다.
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(errorCode, request.getRequestURI(), MDC.get(TRACE_ID_MDC_KEY)));
    }
}
