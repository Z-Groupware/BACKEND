package com.module06.backend.identity.company.infrastructure.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.port.out.LookupRateLimiter;

import lombok.RequiredArgsConstructor;


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
