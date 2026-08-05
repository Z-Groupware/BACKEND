package com.module06.backend.identity.auth.infrastructure.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;

/**
 * Redis 없이 도는 구현. 테스트가 이걸 쓴다.
 *
 * <p>운영 빈으로 등록하지 않는다({@code @Component} 없음) — 여러 인스턴스로 뜨면 서로 다른 목록을
 * 들고 있어서 한쪽에서 로그아웃한 토큰이 다른 쪽에서 통한다.
 */
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<Long, Map<String, Instant>> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(Long memberId, String jti, Duration ttl) {
        tokens.computeIfAbsent(memberId, id -> new ConcurrentHashMap<>())
                .put(jti, Instant.now().plus(ttl));
    }

    @Override
    public boolean exists(Long memberId, String jti) {
        Map<String, Instant> byJti = tokens.get(memberId);
        if (byJti == null) {
            return false;
        }
        Instant expiresAt = byJti.get(jti);
        if (expiresAt == null) {
            return false;
        }
        // Redis 는 TTL 이 지나면 키가 스스로 사라진다. 여기서도 같게 보이도록 읽는 김에 지운다.
        if (expiresAt.isBefore(Instant.now())) {
            byJti.remove(jti);
            return false;
        }
        return true;
    }

    @Override
    public void revoke(Long memberId, String jti) {
        Map<String, Instant> byJti = tokens.get(memberId);
        if (byJti != null) {
            byJti.remove(jti);
        }
    }

    @Override
    public void revokeAllByMember(Long memberId) {
        tokens.remove(memberId);
    }
}
