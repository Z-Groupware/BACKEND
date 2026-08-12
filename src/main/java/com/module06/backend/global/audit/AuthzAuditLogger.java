package com.module06.backend.global.audit;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.module06.backend.global.security.AuthPrincipal;

/**
 * 인가 결정 감사 로그 — 거부된 요청을 "누가(actor) · 어느 회사 소속으로(tenant) · 무엇에(path) ·
 * 무슨 요청을(method) · 왜 막혔는지(code)" 한 줄로 남긴다.
 *
 * <p>왜 필요한가 — 정적 분석(TENANT_001)은 "회사 조건이 빠진 쿼리"를 찾아내지만, 그 구멍이 실제로
 * 악용됐는지는 알려주지 못한다. 교차 회사 접근이 몇 번, 어느 회사 데이터를 향해 일어났는지는
 * 사후에 로그로만 확인할 수 있다. 스캐너가 채울 수 없는 자리다.
 *
 * <p>왜 별도 테이블이 아니라 로그인가 — 감사 테이블은 마이그레이션·보존정책·조회 API가 함께
 * 딸려온다. 지금 필요한 것은 "사고 후 추적이 가능한 상태"이고 검색 가능한 구조화 로그로 충분하다.
 * ⚠️ 한계: 로그는 로테이션으로 사라진다. 장기 보존이 요구되면 수집기로 내보내야 한다.
 *
 * <p>왜 빈이 아니라 정적 메서드인가 — 호출부 중 하나가 {@code GlobalExceptionHandler}
 * ({@code @RestControllerAdvice})다. {@code @WebMvcTest} 슬라이스는 {@code @Configuration} 은
 * 스캔하지 않지만 {@code @RestControllerAdvice} 는 등록하므로, 여기에 빈 의존성을 추가하면
 * 컨트롤러 슬라이스 테스트 16개가 "그 타입의 빈이 없다"로 전부 깨진다. 이 클래스는 상태도
 * 의존성도 없는 로깅 파사드라 정적으로 두는 것이 맞다({@code LoggerFactory} 자체가 그렇다).
 *
 * <p>기록하지 않는 것 — 토큰·요청 본문·이메일 같은 값은 남기지 않는다. 식별자(id)만 남긴다.
 * 감사 로그가 그 자체로 유출 경로가 되면 안 된다.
 */
public final class AuthzAuditLogger {

    /**
     * 애플리케이션 로그와 섞이지 않게 전용 로거 이름을 쓴다. 이 이름으로 별도 appender를 걸거나
     * {@code grep AUTHZ_AUDIT} 으로 감사 기록만 뽑아낼 수 있다.
     */
    public static final String LOGGER_NAME = "AUTHZ_AUDIT";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String ABSENT = "-";

    private AuthzAuditLogger() {
    }

    /** 인증 실패(401) — 토큰이 없거나 유효하지 않아 신원 자체가 확인되지 않은 요청. */
    public static void unauthenticated(HttpServletRequest request, String errorCode) {
        write(AuthzOutcome.UNAUTHENTICATED, request, errorCode);
    }

    /** 인가 실패(403) — 스프링 시큐리티 필터 체인·{@code @PreAuthorize} 단계에서 막힌 요청. */
    public static void deniedByFilter(HttpServletRequest request, String errorCode) {
        write(AuthzOutcome.DENIED_FILTER, request, errorCode);
    }

    /**
     * 인가 실패(403) — 서비스·컨트롤러의 도메인 규칙에서 막힌 요청.
     * 교차 회사 접근 거부는 대부분 이 경로로 들어온다(예: {@code MT_FORBIDDEN_SCOPE}).
     */
    public static void deniedByDomain(HttpServletRequest request, String errorCode) {
        write(AuthzOutcome.DENIED_DOMAIN, request, errorCode);
    }

    /**
     * 키 순서를 고정해 한 줄로 남긴다 — 순서가 흔들리면 grep·파싱이 깨진다.
     *
     * <p>감사 기록 실패가 에러 응답 자체를 망가뜨리면 안 된다. 이 메서드는 호출자의 응답 작성
     * 경로({@code SecurityErrorResponder}·{@code GlobalExceptionHandler})에서 불리므로
     * 어떤 예외도 밖으로 내지 않는다.
     */
    private static void write(AuthzOutcome outcome, HttpServletRequest request, String errorCode) {
        try {
            AuthPrincipal principal = currentPrincipal();
            log.warn("AUTHZ_AUDIT outcome={} actor={} tenant={} authority={} admin={} method={} path={} code={} trace={}",
                    outcome,
                    text(principal == null ? null : principal.memberId()),
                    text(principal == null ? null : principal.companyId()),
                    text(principal == null ? null : principal.authority()),
                    text(principal == null ? null : principal.isAdmin()),
                    text(request == null ? null : request.getMethod()),
                    // 쿼리스트링은 붙이지 않는다 — 검색어 같은 값이 감사 로그로 새는 것을 막는다.
                    text(request == null ? null : request.getRequestURI()),
                    text(errorCode),
                    text(MDC.get(TRACE_ID_MDC_KEY)));
        } catch (RuntimeException ex) {
            log.warn("AUTHZ_AUDIT outcome={} 기록 실패 — 원인: {}", outcome, ex.getClass().getSimpleName());
        }
    }

    private static AuthPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private static String text(Object value) {
        return value == null ? ABSENT : String.valueOf(value);
    }
}
