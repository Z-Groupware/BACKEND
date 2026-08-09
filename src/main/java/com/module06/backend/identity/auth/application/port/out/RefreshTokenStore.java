package com.module06.backend.identity.auth.application.port.out;

import java.time.Duration;

public interface RefreshTokenStore {

    /**
     * 리프레시 토큰 하나를 살아 있는 목록에 올린다.
     *
     * <p>{@code ttl} 이 0 이하면 저장하지 않는다 — 이미 만료된 토큰을 올리는 것과 같기 때문이다.
     * 이 경우 곧바로 {@link #exists} 가 {@code false} 를 준다. Redis 는 SETEX 에 0 이하를 주면
     * 오류를 내므로, 구현마다 갈리지 않도록 계약을 여기서 못박는다.
     */
    void save(Long memberId, String jti, Duration ttl);

    boolean exists(Long memberId, String jti);

    void revoke(Long memberId, String jti);

    void revokeAllByMember(Long memberId);
}
