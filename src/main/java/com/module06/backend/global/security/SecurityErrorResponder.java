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

import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.global.exception.ErrorCode;
import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/*
 * 시큐리티 단계에서 거절된 요청을 프로젝트의 ErrorResponse 로 바꿔 쓴다.
 *
 * 왜 필요한가: JwtAuthenticationFilter 는 토큰이 망가져도 401 을 내리지 않고 통과시킨다
 * (공개 경로도 같은 필터를 지나므로). 그래서 보호 경로의 최종 거절은 GlobalExceptionHandler 가
 * 아니라 시큐리티의 EntryPoint/AccessDeniedHandler 가 한다. 이걸 등록하지 않으면 스프링 기본
 * 구현이 본문 없는 403 을 내려, 프론트가 받기로 한 {errorCode, message, ...} 계약이 깨진다.
 *
 * 두 인터페이스를 한 클래스가 구현하는 이유는 내려보내는 본문 형식이 같기 때문이다 —
 * 코드만 401(AU-006) 과 403(Z-002) 으로 갈린다.
 *
 * traceId 는 GlobalExceptionHandler 와 같은 MDC 키에서 읽는다. 두 경로의 응답이 같은 모양이어야
 * 프론트가 분기하지 않는다.
 *
 * ObjectMapper 는 Jackson 3(tools.jackson)다 — Spring Boot 4 가 HTTP 메시지 변환에 쓰는 그 빈을
 * 그대로 주입받는다. 여기서 new ObjectMapper() 를 만들면 날짜 형식·네이밍 설정이 컨트롤러 응답과
 * 어긋난다. 저장소 안의 com.fasterxml(Jackson 2) 사용처들은 reviewloop 전용이라 빈이 아니다.
 */
@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final ObjectMapper objectMapper;

    /* 인증이 안 된 요청 — 토큰이 없거나 망가졌다. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, AuthErrorCode.UNAUTHORIZED);
    }

    /* 인증은 됐지만 권한이 모자란 요청 — @PreAuthorize 가 막은 경우다. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, CommonErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(errorCode, request.getRequestURI(), MDC.get(TRACE_ID_MDC_KEY)));
    }
}
