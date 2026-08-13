package com.module06.backend.global.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 없이 도는 구현. 테스트가 쓰고, 운영에서는 {@link RedisRateLimiter} 가 대신한다
 * ({@code InMemoryRefreshTokenStore} 가 같은 자리에 있는 것과 같은 이유다).
 *
 * <p>⚠️ 프로세스 안에서만 센다. 인스턴스가 둘이면 한도도 둘이 되므로 운영에서 쓰면 안 된다 —
 * 빈으로 등록하지 않는 이유다({@code @Component} 가 없다).
 */
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(long count, Instant expiresAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Decision record(RateLimitPolicy policy, String subject) {
        Instant now = Instant.now();
        Window window = windows.compute(key(policy, subject), (ignored, current) ->
                current == null || !current.expiresAt().isAfter(now)
                        ? new Window(1, now.plus(policy.window()))
                        : new Window(current.count() + 1, current.expiresAt()));
        return decide(window, policy, now, 0);
    }

    @Override
    public Decision peek(RateLimitPolicy policy, String subject) {
        Instant now = Instant.now();
        Window window = windows.get(key(policy, subject));
        if (window == null || !window.expiresAt().isAfter(now)) {
            return Decision.pass();
        }
        return decide(window, policy, now, 1);
    }

    /** {@code pending} 은 아직 세지 않은 이번 시도의 몫 — record 는 0, peek 은 1이다. */
    private Decision decide(Window window, RateLimitPolicy policy, Instant now, int pending) {
        if (window.count() + pending <= policy.limit()) {
            return Decision.pass();
        }
        // Duration.isPositive() 는 Java 18 부터다 — 이 프로젝트는 17 이라 쓸 수 없다.
        Duration remaining = Duration.between(now, window.expiresAt());
        return new Decision(false, remaining.toMillis() > 0 ? remaining : policy.window());
    }

    private String key(RateLimitPolicy policy, String subject) {
        return policy.name() + ':' + subject;
    }
}
