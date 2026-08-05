package com.module06.backend.identity.auth.infrastructure.persistence;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

/**
 * 운영 구현. 키를 두 종류 쓴다.
 *
 * <pre>
 *   refresh:{memberId}:{jti}   살아 있는 토큰 하나. TTL 을 걸어 스스로 사라진다
 *   refresh:{memberId}         그 사람의 jti Set. 일괄 폐기용
 * </pre>
 *
 * <p>Set 을 따로 두는 이유: 일괄 폐기를 하려면 그 사람의 jti 를 알아야 하는데, {@code KEYS}·
 * {@code SCAN} 으로 훑는 것은 운영 Redis 를 멈추게 하는 짓이라 금지다.
 *
 * <p>개별 키가 Set 보다 먼저 사라질 수 있어 Set 에는 죽은 jti 가 남을 수 있는데,
 * {@link #exists}가 개별 키를 보므로 판정에는 영향이 없다.
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY = "refresh:%d:%s";
    private static final String MEMBER_SET_KEY = "refresh:%d";

    private final StringRedisTemplate redis;

    @Override
    public void save(Long memberId, String jti, Duration ttl) {
        String setKey = memberSetKey(memberId);
        redis.opsForValue().set(tokenKey(memberId, jti), "1", ttl);
        redis.opsForSet().add(setKey, jti);
        extendAtLeast(setKey, ttl);
    }

    /*
     * Set 의 TTL 은 늘리기만 한다. expire() 로 그냥 덮으면 짧은 토큰이 긴 토큰의 Set 수명을 깎는다.
     *
     *   기기 A 를 keepSignedIn=true(14일) 로 로그인 → Set TTL 14일
     *   기기 B 를 keepSignedIn=false(1일) 로 로그인 → Set TTL 이 1일로 줄어든다
     *   1일 뒤 Set 소멸. 그런데 A 의 개별 키는 아직 13일 남아 있다
     *   → revokeAllByMember 가 지울 jti 목록을 못 찾아 A 의 토큰이 살아남는다
     *
     * 일괄 폐기는 재사용 탐지·오프보딩에서 쓰므로 조용히 실패하면 안 된다.
     * getExpire 는 TTL 이 없으면 -1, 키가 없으면 -2 를 준다 — 둘 다 새로 거는 게 맞다.
     */
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
