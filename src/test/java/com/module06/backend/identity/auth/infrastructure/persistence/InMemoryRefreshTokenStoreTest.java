package com.module06.backend.identity.auth.infrastructure.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryRefreshTokenStore")
class InMemoryRefreshTokenStoreTest {

    private static final Duration TTL = Duration.ofDays(1);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private MovableClock clock;
    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(NOW);
        store = new InMemoryRefreshTokenStore(clock);
    }

    /** 만료를 검증하려면 시간이 흘러야 한다. 실제로 기다리는 대신 시계를 민다. */
    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("저장한 토큰은 존재한다")
    void savedTokenExists() {
        store.save(1L, "jti-a", TTL);

        assertThat(store.exists(1L, "jti-a")).isTrue();
    }

    @Test
    @DisplayName("저장하지 않은 토큰은 존재하지 않는다")
    void unsavedTokenDoesNotExist() {
        assertThat(store.exists(1L, "jti-none")).isFalse();
    }

    @Test
    @DisplayName("폐기한 토큰은 사라진다")
    void revokedTokenDisappears() {
        store.save(1L, "jti-a", TTL);

        store.revoke(1L, "jti-a");

        assertThat(store.exists(1L, "jti-a")).isFalse();
    }

    @Test
    @DisplayName("일괄 폐기는 그 사람의 토큰 전부를 지운다")
    void revokeAllClearsEveryTokenOfMember() {
        store.save(1L, "jti-a", TTL);
        store.save(1L, "jti-b", TTL);
        store.save(2L, "jti-c", TTL);

        store.revokeAllByMember(1L);

        assertThat(store.exists(1L, "jti-a")).isFalse();
        assertThat(store.exists(1L, "jti-b")).isFalse();
        assertThat(store.exists(2L, "jti-c")).isTrue();
    }

    @Test
    @DisplayName("TTL 이 지난 토큰은 존재하지 않는다 — Redis 의 자동 만료를 흉내낸다")
    void expiredTokenDoesNotExist() {
        store.save(1L, "jti-a", TTL);

        clock.advance(TTL.plusSeconds(1));

        assertThat(store.exists(1L, "jti-a")).isFalse();
    }

    @Test
    @DisplayName("만료 시각과 정확히 같은 순간의 토큰도 존재하지 않는다 — 남은 TTL 이 0 이면 만료다")
    void tokenAtExactExpiryInstantDoesNotExist() {
        store.save(1L, "jti-a", TTL);

        clock.advance(TTL);

        assertThat(store.exists(1L, "jti-a")).isFalse();
    }

    @Test
    @DisplayName("만료 직전의 토큰은 아직 존재한다")
    void tokenJustBeforeExpiryStillExists() {
        store.save(1L, "jti-a", TTL);

        clock.advance(TTL.minusSeconds(1));

        assertThat(store.exists(1L, "jti-a")).isTrue();
    }

    @Test
    @DisplayName("TTL 이 0 이하면 저장하지 않는다 — Redis 가 SETEX 오류를 내는 입력과 계약을 맞춘다")
    void nonPositiveTtlIsNotStored() {
        store.save(1L, "jti-zero", Duration.ZERO);
        store.save(1L, "jti-negative", Duration.ofSeconds(-1));
        store.save(1L, "jti-null", null);

        assertThat(store.exists(1L, "jti-zero")).isFalse();
        assertThat(store.exists(1L, "jti-negative")).isFalse();
        assertThat(store.exists(1L, "jti-null")).isFalse();
    }

    @Test
    @DisplayName("같은 사람이 기기별로 여러 토큰을 동시에 들 수 있다 — 한쪽 폐기가 다른 쪽을 건드리지 않는다")
    void revokingOneTokenKeepsTheOthers() {
        store.save(1L, "jti-phone", TTL);
        store.save(1L, "jti-laptop", TTL);

        store.revoke(1L, "jti-phone");

        assertThat(store.exists(1L, "jti-phone")).isFalse();
        assertThat(store.exists(1L, "jti-laptop")).isTrue();
    }
}
