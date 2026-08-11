package com.module06.backend.metering.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * isNewerThan은 MeetingStorageUsagePersistenceAdapter/Writer의 락 기반 CAS가 "이 report를
 * 반영할지"를 결정하는 유일한 판정 지점이다 — 순서가 뒤바뀐 report가 최신 값을 덮어써 저장
 * 용량이 과소 집계되지 않도록 하는 핵심 로직이라 도메인 단위로 직접 검증한다.
 */
class MeetingStorageUsageTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 0, 0);

    @Test
    void higherRevisionIsNewer() {
        MeetingStorageUsage stored = MeetingStorageUsage.restore(500L, 7L, 10_000L, 3L, NOW);
        MeetingStorageUsage incoming = MeetingStorageUsage.report(500L, 7L, 20_000L, 5L, NOW);

        assertThat(incoming.isNewerThan(stored)).isTrue();
    }

    @Test
    void equalRevisionIsNotNewer() {
        MeetingStorageUsage stored = MeetingStorageUsage.restore(500L, 7L, 10_000L, 5L, NOW);
        MeetingStorageUsage incoming = MeetingStorageUsage.report(500L, 7L, 999_999L, 5L, NOW);

        assertThat(incoming.isNewerThan(stored)).isFalse();
    }

    @Test
    void lowerRevisionArrivingLateIsNotNewer() {
        // 뒤바뀐 순서로 도착한 report — revision이 더 작으면 usedBytes가 더 커도 무시해야 한다.
        MeetingStorageUsage stored = MeetingStorageUsage.restore(500L, 7L, 20_000L, 5L, NOW);
        MeetingStorageUsage incoming = MeetingStorageUsage.report(500L, 7L, 999_999L, 3L, NOW);

        assertThat(incoming.isNewerThan(stored)).isFalse();
    }

    @Test
    void anyRevisionIsNewerWhenNothingStoredYet() {
        MeetingStorageUsage incoming = MeetingStorageUsage.report(500L, 7L, 1_000L, 0L, NOW);

        assertThat(incoming.isNewerThan(null)).isTrue();
    }
}
