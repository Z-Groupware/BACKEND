package com.module06.backend.identity.company.infrastructure.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.port.out.LookupRateLimiter;

import lombok.RequiredArgsConstructor;

/**
 * IP 당 분당 20회로 제한한다.
 *
 * <p>{@code INCR} 로 세고 첫 증가에만 TTL 을 건다 — 매번 걸면 창이 계속 밀려서 제한이 풀리지 않는다.
 * 고정 창(fixed window)이라 경계에서 최대 2배까지 통과할 수 있는데, 목적이 정확한 제어가 아니라
 * 코드 열거 속도를 떨어뜨리는 것이므로 이 정도로 충분하다.
 */
@Component
@RequiredArgsConstructor
public class RedisLookupRateLimiter implements LookupRateLimiter {

    private static final int LIMIT_PER_MINUTE = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    @Override
    public void checkOrThrow(String clientIp) {
        String key = "ratelimit:lookup:%s".formatted(clientIp);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        if (count != null && count > LIMIT_PER_MINUTE) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
