package com.module06.backend.identity.auth.application.port.out;

import java.time.Duration;

public interface RefreshTokenStore {

    void save(Long memberId, String jti, Duration ttl);

    boolean exists(Long memberId, String jti);

    void revoke(Long memberId, String jti);

    void revokeAllByMember(Long memberId);
}
