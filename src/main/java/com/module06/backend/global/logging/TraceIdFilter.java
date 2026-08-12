package com.module06.backend.global.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        try {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            /*
             * 여기서 한 번 더 남긴다. GlobalExceptionHandler 의 마지막 그물은 DispatcherServlet 이
             * 예외를 해석하는 경로에만 걸린다 — 뒤쪽 필터(예: 시큐리티 체인)에서 터진 예외는 그
             * 그물에 닿지 않고 컨테이너까지 그대로 올라가서, 스택트레이스가 traceId 없이 찍힌다.
             * 게다가 finally 가 MDC 를 지운 뒤에 로깅되므로 %X{traceId} 로도 복구되지 않는다.
             *
             * 응답의 X-Trace-Id 로 로그를 되짚는 게 이 필터의 존재 이유라, 그 대조가 끊기는
             * 구간을 남겨두지 않는다. 예외는 그대로 다시 던진다 — 상태 코드 결정은 여전히
             * 컨테이너·에러 페이지 몫이고, 여기서 삼키면 응답이 200 으로 나갈 수 있다.
             */
            log.error("필터 경계에서 처리되지 않은 예외 — traceId={} {} {}",
                    traceId, request.getMethod(), request.getRequestURI(), ex);
            throw ex;
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
