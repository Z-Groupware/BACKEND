package com.module06.backend.identity.auth.infrastructure.persistence;

import java.time.Clock;
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

    private final Clock clock;

    public InMemoryRefreshTokenStore() {
        this(Clock.systemUTC());
    }

    /** 만료 경계를 검증하려면 시계를 앞으로 돌려야 한다. 테스트만 이 생성자를 쓴다. */
    InMemoryRefreshTokenStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(Long memberId, String jti, Duration ttl) {
        if (isNotPositive(ttl)) {
            return;
        }
        tokens.computeIfAbsent(memberId, id -> new ConcurrentHashMap<>())
                .put(jti, clock.instant().plus(ttl));
    }

    private boolean isNotPositive(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative();
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
        // 만료 시각이 '지금' 과 같은 것도 만료로 본다 — TTL 이 남았다고 말할 수 없기 때문이다.
        // 지울 때 값까지 맞춰 지운다. 그 사이 같은 jti 로 새로 저장됐다면 그건 살아 있는 토큰이다.
        if (!expiresAt.isAfter(clock.instant())) {
            byJti.remove(jti, expiresAt);
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
