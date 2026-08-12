package com.module06.backend.global.audit;

/**
 * 인가 결정의 결과 구분. 같은 403이라도 어느 층에서 막혔는지를 나눠 기록한다 —
 * 집행 위치가 다르면 조사 방법도 다르기 때문이다.
 */
public enum AuthzOutcome {

    /** 401 — 신원 확인 실패. 토큰이 없거나 유효하지 않다. */
    UNAUTHENTICATED,

    /** 403 — 스프링 시큐리티 필터 체인·{@code @PreAuthorize} 에서 거부. */
    DENIED_FILTER,

    /** 403 — 서비스·컨트롤러의 도메인 규칙에서 거부. 교차 회사 거부가 주로 여기로 온다. */
    DENIED_DOMAIN
}
