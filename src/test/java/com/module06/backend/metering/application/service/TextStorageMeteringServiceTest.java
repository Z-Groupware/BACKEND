package com.module06.backend.metering.application.service;

import com.module06.backend.metering.application.command.ReportMeetingTextStorageUsageCommand;
import com.module06.backend.metering.domain.model.TextStorageSource;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TextStorageMeteringServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long PROJECT = 9L;
    private static final Long MEETING = 500L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;

    private TextStorageMeteringService service;

    @BeforeEach
    void setUp() {
        service = new TextStorageMeteringService(meetingTextStorageUsageRepository, FIXED_CLOCK);
    }

    @Test
    void reportDelegatesToRepositoryWithFixedClockTimestamp() {
        service.report(new ReportMeetingTextStorageUsageCommand(
                COMPANY, PROJECT, MEETING, TextStorageSource.CAPTION, 12_345L, 1L));

        verify(meetingTextStorageUsageRepository).reportIfNewer(
                eq(MEETING), eq(COMPANY), eq(PROJECT), eq(TextStorageSource.CAPTION), eq(12_345L), eq(1L),
                eq(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone())));
    }

    @Test
    void reportDelegatesEveryCallToRepositoryRegardlessOfSourceOrRevisionOrdering() {
        // 소스 구분·revision 비교·무시 판정은 MeetingTextStorageUsageRepository 구현(락 기반 병합)의
        // 책임이다 — 서비스는 매번 그대로 위임만 한다는 걸 확인한다.
        service.report(new ReportMeetingTextStorageUsageCommand(
                COMPANY, PROJECT, MEETING, TextStorageSource.TRANSCRIPT, 20_000L, 5L));
        service.report(new ReportMeetingTextStorageUsageCommand(
                COMPANY, PROJECT, MEETING, TextStorageSource.SUMMARY, 10_000L, 3L));

        verify(meetingTextStorageUsageRepository, times(1)).reportIfNewer(
                eq(MEETING), eq(COMPANY), eq(PROJECT), eq(TextStorageSource.TRANSCRIPT), eq(20_000L), eq(5L), eq(
                        LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone())));
        verify(meetingTextStorageUsageRepository, times(1)).reportIfNewer(
                eq(MEETING), eq(COMPANY), eq(PROJECT), eq(TextStorageSource.SUMMARY), eq(10_000L), eq(3L), eq(
                        LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone())));
    }
}
