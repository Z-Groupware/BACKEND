package com.module06.backend.global.ratelimit;

import java.time.Duration;

/**
 * 한 종류의 제한. {@code name} 은 Redis 키 접두어로 쓰이므로 정책끼리 카운터가 섞이지 않는다.
 *
 * @param name   키 접두어 겸 감사 로그에 남는 이름
 * @param limit  {@code window} 안에서 허용할 횟수
 * @param window 창 길이
 */
public record RateLimitPolicy(String name, int limit, Duration window) {

    public RateLimitPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("정책 이름이 비었다 — Redis 키 접두어라 비면 카운터가 섞인다.");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit 은 1 이상이어야 한다: " + limit);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window 는 양수여야 한다: " + window);
        }
    }
}
