package com.module06.backend.identity.auth.application.port.out;

import java.time.Duration;

/**
 * 살아 있는 리프레시 토큰 목록.
 *
 * <p>토큰에도 만료 시각이 적혀 있지만 그것만으로는 부족하다 — 한 번 발급된 JWT 는 서버가 취소할 수
 * 없어서 로그아웃을 눌러도 적힌 시각까지 계속 통한다. 그래서 "지금 유효한 것"을 서버가 따로 들고,
 * 목록에 없는 토큰을 거부한다.
 *
 * <p>인터페이스로 두는 이유는 두 가지다. (1) 테스트가 H2 로 돌고 Redis 가 없다 — 인메모리 구현을
 * 끼우면 Redis 없이 검증된다. (2) {@link #revokeAllByMember}를 인수인계 담당이 오프보딩
 * 트랜잭션에서 호출한다 — 계약에 의존해야 그쪽 코드가 Redis 를 몰라도 된다.
 */
public interface RefreshTokenStore {

    void save(Long memberId, String jti, Duration ttl);

    boolean exists(Long memberId, String jti);

    void revoke(Long memberId, String jti);

    /** 그 구성원의 모든 리프레시를 폐기한다. 재사용 탐지·권한 변경·오프보딩에서 쓴다. */
    void revokeAllByMember(Long memberId);
}
