package com.module06.backend.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 필터 경계에서 터진 예외가 traceId 와 함께 남는지 본다. GlobalExceptionHandler 의 마지막 그물은
 * DispatcherServlet 경로만 덮으므로, 뒤쪽 필터에서 터지면 이 필터가 남기지 않는 한 traceId 가
 * 붙은 로그 라인이 아예 생기지 않는다(코드래빗 지적).
 */
@DisplayName("요청 추적 필터(TraceIdFilter)")
class TraceIdFilterTest {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final Logger logger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("필터 경계에서 예외가 터지면 응답 헤더의 traceId 로 로그를 남기고 그대로 다시 던진다")
    void logsWithTraceIdAndRethrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/companies/me/onboarding");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("필터 경계에서 터진 예외");
        })).isInstanceOf(IllegalStateException.class);

        String traceId = response.getHeader("X-Trace-Id");
        assertThat(traceId).isNotBlank();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains(traceId)
                .contains("POST")
                .contains("/api/companies/me/onboarding");
        assertThat(appender.list.get(0).getThrowableProxy().getMessage()).isEqualTo("필터 경계에서 터진 예외");
    }

    @Test
    @DisplayName("예외가 나가도 MDC 는 비운다 — 스레드가 재사용돼도 남의 traceId 가 묻지 않는다")
    void clearsMdcEvenOnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/members");

        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new IllegalStateException("필터 경계에서 터진 예외");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("정상 요청은 아무것도 남기지 않는다 — 예외 없는 경로에 로그가 끼지 않는다")
    void staysQuietOnSuccess() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(new MockHttpServletRequest("GET", "/api/members"), response, (req, res) -> {
        });

        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        assertThat(appender.list).isEmpty();
    }
}
