package com.module06.backend.identity.auth.infrastructure.persistence;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY = "refresh:%d:%s";
    private static final String MEMBER_SET_KEY = "refresh:%d";

    private final StringRedisTemplate redis;

    @Override
    public void save(Long memberId, String jti, Duration ttl) {
        // Redis 는 SETEX 에 0 이하를 주면 오류를 낸다. 이미 만료된 토큰이므로 올리지 않고 끝낸다.
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String setKey = memberSetKey(memberId);
        redis.opsForValue().set(tokenKey(memberId, jti), "1", ttl);
        redis.opsForSet().add(setKey, jti);
        extendAtLeast(setKey, ttl);
    }

    private void extendAtLeast(String setKey, Duration ttl) {
        Long remainingSeconds = redis.getExpire(setKey, TimeUnit.SECONDS);
        if (remainingSeconds == null || remainingSeconds < 0 || remainingSeconds < ttl.toSeconds()) {
            redis.expire(setKey, ttl);
        }
    }

    @Override
    public boolean exists(Long memberId, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(tokenKey(memberId, jti)));
    }

    @Override
    public void revoke(Long memberId, String jti) {
        redis.delete(tokenKey(memberId, jti));
        redis.opsForSet().remove(memberSetKey(memberId), jti);
    }

    @Override
    public void revokeAllByMember(Long memberId) {
        String setKey = memberSetKey(memberId);
        Set<String> jtis = redis.opsForSet().members(setKey);
        if (jtis != null && !jtis.isEmpty()) {
            List<String> keys = jtis.stream().map(jti -> tokenKey(memberId, jti)).toList();
            redis.delete(keys);
        }
        redis.delete(setKey);
    }

    private String tokenKey(Long memberId, String jti) {
        return TOKEN_KEY.formatted(memberId, jti);
    }

    private String memberSetKey(Long memberId) {
        return MEMBER_SET_KEY.formatted(memberId);
    }
}
