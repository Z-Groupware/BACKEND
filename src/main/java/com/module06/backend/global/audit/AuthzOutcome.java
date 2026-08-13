package com.module06.backend.global.audit;

/**
 * 인가·인증 결정의 결과 구분. 같은 403이라도 어느 층에서 막혔는지를 나눠 기록한다 —
 * 집행 위치가 다르면 조사 방법도 다르기 때문이다.
 *
 * <p>{@code alarming} 은 로그 레벨을 가른다. 대부분은 정상 운영 중에도 꾸준히 나오는 값이라
 * WARN 이 맞고(권한 없는 화면을 눌러본 사용자, 만료된 토큰), 사람이 즉시 봐야 하는 것만 ERROR 다.
 * 전부 같은 레벨로 남기면 진짜 사건이 일상 잡음에 묻힌다.
 */
public enum AuthzOutcome {

    /** 401 — 신원 확인 실패. 토큰이 없거나 유효하지 않다. */
    UNAUTHENTICATED(false),

    /** 403 — 스프링 시큐리티 필터 체인·{@code @PreAuthorize} 에서 거부. */
    DENIED_FILTER(false),

    /** 403 — 서비스·컨트롤러의 도메인 규칙에서 거부. 교차 회사 거부가 주로 여기로 온다. */
    DENIED_DOMAIN(false),

    /**
     * 401 — 자격증명이 맞지 않아 로그인 실패.
     *
     * <p>한 건은 오타지만 급증은 무차별 대입이다. 즉 값이 아니라 <b>빈도</b>가 신호라서, 개별
     * 기록보다 시계열이 중요하다. 어느 계정을 노렸는지는 남기지 않는다 — 이메일을 기록하면
     * 감사 로그 자체가 계정 목록이 된다(이 클래스의 기록 정책).
     */
    AUTH_FAILED(false),

    /**
     * 401 — 이미 쓴 갱신표가 다시 왔다.
     *
     * <p>로테이션이 정상 동작하면 일어날 수 없다. 표는 쓰는 즉시 폐기되므로, 같은 표가 두 번
     * 온다는 것은 그 표가 <b>복제됐다</b>는 뜻이다. 이 목록에서 유일하게 ERROR 인 이유이고,
     * 알림을 건다면 이 한 줄이다.
     */
    TOKEN_REUSED(true);

    private final boolean alarming;

    AuthzOutcome(boolean alarming) {
        this.alarming = alarming;
    }

    /** true 면 ERROR 로 남긴다 — 사람이 봐야 하는 사건이다. */
    public boolean alarming() {
        return alarming;
    }
}
