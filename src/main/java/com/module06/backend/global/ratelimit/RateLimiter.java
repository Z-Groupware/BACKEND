package com.module06.backend.global.ratelimit;

import java.time.Duration;

/**
 * 횟수 제한. 구현은 {@link RedisRateLimiter} 하나뿐이지만, 테스트가 Redis 없이 돌 수 있도록
 * 인터페이스로 둔다(갱신표 저장소가 {@code RefreshTokenStore} 로 나뉜 것과 같은 이유다).
 */
public interface RateLimiter {

    /** 판정 결과. {@code retryAfter} 는 거부됐을 때만 의미가 있다(허용이면 0). */
    record Decision(boolean allowed, Duration retryAfter) {

        /** 이름이 {@code allowed} 가 아닌 이유는 레코드 접근자와 겹치기 때문이다. */
        public static Decision pass() {
            return new Decision(true, Duration.ZERO);
        }
    }

    /**
     * 시도를 1회 계상하고 판정한다. 창 안에서 {@code limit} 번째까지는 허용, 그 다음부터 거부다.
     *
     * @param subject 무엇을 세는 단위(IP · 계정 등). 키에 그대로 들어가므로 개인정보는
     *                호출부에서 해싱해 넘긴다({@link RateLimitSubject}).
     */
    Decision record(RateLimitPolicy policy, String subject);

    /**
     * 계상하지 않고 지금 막혀 있는지만 본다.
     *
     * <p>로그인처럼 <b>실패만 세는</b> 자리에 필요하다 — 시작할 때는 보기만 하고, 실패했을 때만
     * {@link #record} 로 올린다. 성공한 로그인이 카운터를 밀어 올리면 정상 사용자가 자기 로그인으로
     * 잠기게 된다.
     */
    Decision peek(RateLimitPolicy policy, String subject);
}
