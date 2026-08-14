package com.module06.backend.global.ratelimit;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 제한의 계약을 여기서 못박는다 — 운영 구현(RedisRateLimiter)은 같은 계약을 Lua 로 옮긴 것이다.
 * 경계(한도 그 자체는 통과, 그 다음부터 거부)와 격리(정책·대상이 다르면 카운터가 섞이지 않는다)가
 * 핵심이다. 섞이면 한 사람의 실패가 남을 잠근다.
 */
@DisplayName("RateLimiter 계약")
class InMemoryRateLimiterTest {

    private static final RateLimitPolicy POLICY = new RateLimitPolicy("test", 3, Duration.ofMinutes(1));

    private final RateLimiter limiter = new InMemoryRateLimiter();

    @Test
    @DisplayName("한도까지는 통과하고 그 다음부터 거부한다 — 경계에서 한 번 더 통과하면 한도가 틀린 것이다")
    void allowsUpToLimitThenRejects() {
        assertThat(limiter.record(POLICY, "a").allowed()).isTrue();
        assertThat(limiter.record(POLICY, "a").allowed()).isTrue();
        assertThat(limiter.record(POLICY, "a").allowed()).isTrue();

        assertThat(limiter.record(POLICY, "a").allowed()).isFalse();
    }

    @Test
    @DisplayName("거부하면 재시도까지 남은 시간을 준다 — 0초를 주면 즉시 다시 두들긴다")
    void rejectionCarriesRetryAfter() {
        for (int i = 0; i < 4; i++) {
            limiter.record(POLICY, "a");
        }

        Duration retryAfter = limiter.record(POLICY, "a").retryAfter();

        assertThat(retryAfter).isPositive().isLessThanOrEqualTo(POLICY.window());
    }

    @Test
    @DisplayName("대상이 다르면 카운터가 섞이지 않는다 — 섞이면 한 사람의 실패가 남을 잠근다")
    void countsPerSubject() {
        for (int i = 0; i < 4; i++) {
            limiter.record(POLICY, "a");
        }

        assertThat(limiter.record(POLICY, "b").allowed()).isTrue();
    }

    @Test
    @DisplayName("정책이 다르면 카운터가 섞이지 않는다 — 로그인 실패가 재발급을 막으면 안 된다")
    void countsPerPolicy() {
        RateLimitPolicy other = new RateLimitPolicy("other", 3, Duration.ofMinutes(1));
        for (int i = 0; i < 4; i++) {
            limiter.record(POLICY, "a");
        }

        assertThat(limiter.record(other, "a").allowed()).isTrue();
    }

    @Test
    @DisplayName("peek 은 계상하지 않는다 — 여기서 세면 성공한 로그인이 카운터를 밀어 올린다")
    void peekDoesNotCount() {
        for (int i = 0; i < 10; i++) {
            limiter.peek(POLICY, "a");
        }

        assertThat(limiter.record(POLICY, "a").allowed()).isTrue();
    }

    @Test
    @DisplayName("peek 은 이미 넘긴 상태를 그대로 알려준다")
    void peekReflectsExceededState() {
        for (int i = 0; i < 4; i++) {
            limiter.record(POLICY, "a");
        }

        assertThat(limiter.peek(POLICY, "a").allowed()).isFalse();
    }

    @Test
    @DisplayName("창이 지나면 다시 열린다 — 영구 차단이 되면 정상 사용자가 복구할 수 없다")
    void windowExpires() throws InterruptedException {
        RateLimitPolicy shortWindow = new RateLimitPolicy("short", 1, Duration.ofMillis(50));
        assertThat(limiter.record(shortWindow, "a").allowed()).isTrue();
        assertThat(limiter.record(shortWindow, "a").allowed()).isFalse();

        Thread.sleep(80);

        assertThat(limiter.record(shortWindow, "a").allowed()).isTrue();
    }

    @Test
    @DisplayName("잘못된 정책은 만들 때 걸린다 — 0 이면 아무도 못 들어오고, 이름이 비면 카운터가 섞인다")
    void rejectsInvalidPolicy() {
        assertThat(catchType(() -> new RateLimitPolicy("x", 0, Duration.ofMinutes(1))))
                .isEqualTo(IllegalArgumentException.class);
        assertThat(catchType(() -> new RateLimitPolicy(" ", 1, Duration.ofMinutes(1))))
                .isEqualTo(IllegalArgumentException.class);
        assertThat(catchType(() -> new RateLimitPolicy("x", 1, Duration.ZERO)))
                .isEqualTo(IllegalArgumentException.class);
    }

    private Class<?> catchType(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (RuntimeException ex) {
            return ex.getClass();
        }
    }
}
