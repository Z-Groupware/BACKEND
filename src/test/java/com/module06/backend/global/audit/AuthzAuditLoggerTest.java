package com.module06.backend.global.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.module06.backend.global.security.AuthPrincipal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 감사 로그의 계약은 "출력되는 한 줄" 자체다 — 조사할 때 grep 하는 대상이 그 줄이기 때문에,
 * 목(mock) 호출 여부가 아니라 실제로 찍힌 문자열을 검증한다.
 */
@DisplayName("AuthzAuditLogger")
class AuthzAuditLoggerTest {

    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger(AuthzAuditLogger.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        auditLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void cleanUp() {
        auditLogger.detachAppender(appender);
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("도메인 거부는 행위자·회사·권한·경로·에러코드를 남긴다 — 교차 회사 접근 조사에 필요한 값 전부")
    void deniedByDomain_기록() {
        authenticateAs(new AuthPrincipal(42L, 7L, "MEMBER", false, 3L));
        MDC.put("traceId", "trace-abc");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/metering/token-plan");

        AuthzAuditLogger.deniedByDomain(request, "MT-001");

        assertThat(loggedLine())
                .contains("outcome=DENIED_DOMAIN")
                .contains("actor=42")
                .contains("tenant=7")
                .contains("authority=MEMBER")
                .contains("admin=false")
                .contains("method=GET")
                .contains("path=/api/v1/metering/token-plan")
                .contains("code=MT-001")
                .contains("trace=trace-abc");
    }

    @Test
    @DisplayName("신원이 없는 요청은 actor·tenant를 '-'로 남긴다 — 값이 비어 줄이 깨지지 않는다")
    void unauthenticated_주체없음() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/meetings");

        AuthzAuditLogger.unauthenticated(request, "AU-001");

        assertThat(loggedLine())
                .contains("outcome=UNAUTHENTICATED")
                .contains("actor=-")
                .contains("tenant=-")
                .contains("code=AU-001");
    }

    @Test
    @DisplayName("쿼리스트링은 남기지 않는다 — 검색어 같은 값이 감사 로그로 새면 안 된다")
    void 쿼리스트링_제외() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
        request.setQueryString("keyword=김철수&email=a@b.com");

        AuthzAuditLogger.deniedByFilter(request, "Z-002");

        assertThat(loggedLine())
                .contains("path=/api/v1/members")
                .doesNotContain("keyword")
                .doesNotContain("a@b.com");
    }

    @Test
    @DisplayName("요청이 null이어도 예외를 내지 않는다 — 감사 기록 실패가 에러 응답을 망가뜨리면 안 된다")
    void 기록실패가_응답을_깨지_않는다() {
        assertThatCode(() -> AuthzAuditLogger.deniedByDomain(null, null))
                .doesNotThrowAnyException();

        assertThat(loggedLine())
                .contains("method=-")
                .contains("path=-")
                .contains("code=-");
    }

    @Test
    @DisplayName("주체가 AuthPrincipal이 아니면 신원 없는 것으로 남긴다 — 익명 인증 토큰에도 안전하다")
    void 주체타입이_다르면_무시() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/meetings/991");

        AuthzAuditLogger.deniedByFilter(request, "Z-002");

        assertThat(loggedLine()).contains("actor=-").contains("tenant=-");
    }

    /*
     * 아래 두 건이 SECURITY_AUDIT_HANDOVER.md P1 #5 가 가리키던 자리다 — 401 계열 인증 실패는
     * 어디에도 남지 않았다. 특히 재사용 탐지는 코드 주석이 "탈취 정황"이라고 명시하면서도
     * 알림이 없어, 발생해도 아무도 모르는 상태였다.
     */

    @Test
    @DisplayName("재사용 탐지는 ERROR 로 남긴다 — 이 목록에서 유일하게 사람이 즉시 봐야 하는 사건이다")
    void 재사용탐지_ERROR레벨() {
        AuthzAuditLogger.refreshTokenReused(42L, "AU-005");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("재사용 탐지는 누구의 표였는지를 남긴다 — 재발급은 공개라 주체가 없어 memberId 를 직접 받는다")
    void 재사용탐지_행위자기록() {
        MDC.put("traceId", "trace-reuse");

        AuthzAuditLogger.refreshTokenReused(42L, "AU-005");

        assertThat(loggedLine())
                .contains("outcome=TOKEN_REUSED")
                .contains("actor=42")
                .contains("code=AU-005")
                .contains("trace=trace-reuse");
    }

    @Test
    @DisplayName("인증된 주체가 있으면 그 memberId 가 이긴다 — 넘겨받은 값으로 actor 를 덮어쓸 수 없다")
    void 행위자는_위조되지_않는다() {
        authenticateAs(new AuthPrincipal(7L, 1L, "MEMBER", false, null));

        AuthzAuditLogger.refreshTokenReused(999L, "AU-005");

        assertThat(loggedLine()).contains("actor=7").doesNotContain("actor=999");
    }

    @Test
    @DisplayName("로그인 실패는 WARN 으로 남기고 시도한 계정은 남기지 않는다 — 감사 로그가 계정 목록이 되면 안 된다")
    void 로그인실패_기록() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        AuthzAuditLogger.loginFailed(request, "AU-002");

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(loggedLine())
                .contains("outcome=AUTH_FAILED")
                .contains("actor=-")
                .contains("path=/api/auth/login")
                .contains("code=AU-002");
    }

    private void authenticateAs(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private String loggedLine() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }
}
