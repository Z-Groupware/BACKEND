package com.module06.backend.metering.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * withSourceReportIfNewer는 MeetingTextStorageUsagePersistenceAdapter/Writer의 락 기반 병합이
 * "이 소스의 report를 반영할지"를 결정하는 유일한 판정 지점이다 — 세 소스(캡션·transcript·요약)가
 * 서로의 컬럼을 건드리지 않고, 각자 자기 소스의 revision만 비교해 뒤바뀐 순서를 걸러내는지 도메인
 * 단위로 직접 검증한다.
 */
class MeetingTextStorageUsageTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 0, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 8, 14, 0, 5);

    @Test
    void firstReportFillsOnlyThatSourceAndLeavesOthersZero() {
        MeetingTextStorageUsage usage = MeetingTextStorageUsage.firstReport(500L, 7L, 9L,
                TextStorageSource.CAPTION, 1_000L, 100L, NOW);

        assertThat(usage.getCaptionBytes()).isEqualTo(1_000L);
        assertThat(usage.getCaptionRevision()).isEqualTo(100L);
        assertThat(usage.getTranscriptBytes()).isZero();
        assertThat(usage.getSummaryBytes()).isZero();
        assertThat(usage.getTotalUsedBytes()).isEqualTo(1_000L);
    }

    @Test
    void higherRevisionUpdatesOnlyThatSourceColumn() {
        MeetingTextStorageUsage stored = MeetingTextStorageUsage.restore(500L, 7L, 9L,
                1_000L, 100L, 2_000L, 200L, 3_000L, 300L, NOW);

        MeetingTextStorageUsage merged = stored.withSourceReportIfNewer(TextStorageSource.TRANSCRIPT,
                5_000L, 250L, LATER);

        assertThat(merged.getTranscriptBytes()).isEqualTo(5_000L);
        assertThat(merged.getTranscriptRevision()).isEqualTo(250L);
        // 다른 두 소스는 그대로 보존된다 — 이게 이 클래스를 만든 이유다.
        assertThat(merged.getCaptionBytes()).isEqualTo(1_000L);
        assertThat(merged.getSummaryBytes()).isEqualTo(3_000L);
        assertThat(merged.getTotalUsedBytes()).isEqualTo(1_000L + 5_000L + 3_000L);
    }

    @Test
    void equalOrLowerRevisionIsIgnoredAndReturnsSameInstance() {
        MeetingTextStorageUsage stored = MeetingTextStorageUsage.restore(500L, 7L, 9L,
                1_000L, 100L, 2_000L, 200L, 3_000L, 300L, NOW);

        MeetingTextStorageUsage merged = stored.withSourceReportIfNewer(TextStorageSource.CAPTION,
                999_999L, 100L, LATER);

        // 반영 안 됐으면 같은 인스턴스를 그대로 돌려준다 — 호출자가 "바뀐 게 없다"를 구분하는 방법.
        assertThat(merged).isSameAs(stored);
    }

    @Test
    void unrelatedSourceRevisionDoesNotBlockAnotherSource() {
        // caption의 revision이 낮아도(100) transcript는 자기 revision(200)만 비교한다 —
        // 소스별로 revision이 독립이라는 걸 확인한다.
        MeetingTextStorageUsage stored = MeetingTextStorageUsage.firstReport(500L, 7L, 9L,
                TextStorageSource.CAPTION, 1_000L, 100L, NOW);

        MeetingTextStorageUsage merged = stored.withSourceReportIfNewer(TextStorageSource.TRANSCRIPT,
                2_000L, 1L, LATER);

        assertThat(merged.getTranscriptBytes()).isEqualTo(2_000L);
        assertThat(merged.getTranscriptRevision()).isEqualTo(1L);
    }
}
