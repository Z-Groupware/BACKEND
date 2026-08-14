package com.module06.backend.global.ratelimit;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.module06.backend.global.audit.AuthzAuditLogger;
import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.global.ratelimit.RateLimitProperties.IpRule;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 공개 엔드포인트의 IP 기준 제한. 인증 전에 돌아, 토큰 없이 들어오는 대량 요청을 앞에서 끊는다.
 *
 * <p>여기서 IP 만 보는 이유 — 계정 기준 제한은 기업코드·이메일이 필요한데 그 값은 요청 본문에
 * 있다. 필터에서 본문을 읽으면 컨트롤러가 다시 읽을 수 없어 래퍼로 버퍼링해야 하고, 그 래퍼는
 * 모든 요청의 본문을 메모리에 올린다. 본문은 이미 파싱된 자리(AuthService.login)에서 보는 편이
 * 싸고 안전하다 — 두 층이 각자 볼 수 있는 것을 본다.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        IpRule rule = matchingRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision =
                rateLimiter.record(rule.policy(), RateLimitSubject.ofClientIp(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        AuthzAuditLogger.rateLimited(request, AuthErrorCode.TOO_MANY_REQUESTS.getCode(), rule.policy().name());
        writeTooManyRequests(request, response, decision);
    }

    private IpRule matchingRule(HttpServletRequest request) {
        // 쿼리스트링·경로변수가 없는 고정 경로들이라 정확히 비교한다. 접두어로 비교하면
        // /api/auth/login-history 같은 경로가 나중에 생겼을 때 조용히 함께 묶인다.
        String path = request.getRequestURI();
        String method = request.getMethod();
        return properties.ipRules().stream()
                .filter(rule -> rule.matches(method, path))
                .findFirst()
                .orElse(null);
    }

    /**
     * 본문은 다른 에러와 같은 {@code ErrorResponse} 형태로 낸다 — 프론트가 429 만 다르게 파싱하지
     * 않게. {@code Retry-After} 는 표준 헤더라 재시도 시점을 코드 없이도 알 수 있다.
     */
    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                      RateLimiter.Decision decision) throws IOException {
        response.setStatus(AuthErrorCode.TOO_MANY_REQUESTS.getHttpStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfter().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                AuthErrorCode.TOO_MANY_REQUESTS, request.getRequestURI(), MDC.get(TRACE_ID_MDC_KEY)));
    }
}
