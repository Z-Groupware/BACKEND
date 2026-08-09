package com.module06.backend.identity.auth.infrastructure.persistence;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY = "refresh:%d:%s";
    private static final String TOKEN_KEY_PREFIX = "refresh:%d:";
    private static final String MEMBER_SET_KEY = "refresh:%d";

    /** KEYS[1]=토큰 키, KEYS[2]=jti Set, ARGV[1]=jti, ARGV[2]=TTL(ms). */
    private static final RedisScript<Long> SAVE = RedisScript.of("""
            local ttl = tonumber(ARGV[2])
            redis.call('SET', KEYS[1], '1', 'PX', ttl)
            redis.call('SADD', KEYS[2], ARGV[1])
            local remaining = redis.call('PTTL', KEYS[2])
            if remaining < 0 or remaining < ttl then
                redis.call('PEXPIRE', KEYS[2], ttl)
            end
            return 1
            """, Long.class);

    /** KEYS[1]=jti Set, ARGV[1]=토큰 키 접두어. */
    private static final RedisScript<Long> REVOKE_ALL = RedisScript.of("""
            local jtis = redis.call('SMEMBERS', KEYS[1])
            for i = 1, #jtis do
                redis.call('DEL', ARGV[1] .. jtis[i])
            end
            redis.call('DEL', KEYS[1])
            return #jtis
            """, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public void save(Long memberId, String jti, Duration ttl) {
        // Redis 는 SET 에 0 이하 만료를 주면 오류를 낸다. 이미 만료된 토큰이므로 올리지 않고 끝낸다.
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redis.execute(SAVE,
                List.of(tokenKey(memberId, jti), memberSetKey(memberId)),
                jti, String.valueOf(ttl.toMillis()));
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
        redis.execute(REVOKE_ALL, List.of(memberSetKey(memberId)), tokenKeyPrefix(memberId));
    }

    private String tokenKey(Long memberId, String jti) {
        return TOKEN_KEY.formatted(memberId, jti);
    }

    private String tokenKeyPrefix(Long memberId) {
        return TOKEN_KEY_PREFIX.formatted(memberId);
    }

    private String memberSetKey(Long memberId) {
        return MEMBER_SET_KEY.formatted(memberId);
    }
}
