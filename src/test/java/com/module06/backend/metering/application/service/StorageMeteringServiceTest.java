package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.result.StorageQuotaStatusResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageMeteringServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long PROJECT = 9L;
    private static final Long MEETING = 500L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private MeetingStorageUsageRepository meetingStorageUsageRepository;

    @Mock
    private CompanyStoragePlanRepository companyStoragePlanRepository;

    private StorageMeteringService service;

    @BeforeEach
    void setUp() {
        service = new StorageMeteringService(meetingStorageUsageRepository, companyStoragePlanRepository,
                FIXED_CLOCK);
    }

    @Test
    void reportSavesSnapshotWithFixedClockTimestamp() {
        ArgumentCaptor<MeetingStorageUsage> captor = ArgumentCaptor.forClass(MeetingStorageUsage.class);

        service.report(new ReportMeetingStorageUsageCommand(COMPANY, PROJECT, MEETING, 12_345L, 1L));

        verify(meetingStorageUsageRepository).reportIfNewer(captor.capture());
        MeetingStorageUsage saved = captor.getValue();
        assertThat(saved.getMeetingId()).isEqualTo(MEETING);
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY);
        assertThat(saved.getUsedBytes()).isEqualTo(12_345L);
        assertThat(saved.getRevision()).isEqualTo(1L);
        // 서비스가 실제로 주입된 Clock을 쓰는지 검증 — null이 아닌지만 보면 시스템 시간을 써도
        // 통과해버린다(CodeRabbit 지적).
        assertThat(saved.getUpdatedAt())
                .isEqualTo(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone()));
    }

    @Test
    void reportDelegatesEveryCallToRepositoryRegardlessOfRevisionOrdering() {
        // revision 비교·무시 판정은 MeetingStorageUsageRepository 구현(락 기반 CAS)의 책임이다 —
        // 서비스는 매번 그대로 위임만 한다는 걸 확인한다.
        service.report(new ReportMeetingStorageUsageCommand(COMPANY, PROJECT, MEETING, 20_000L, 5L));
        service.report(new ReportMeetingStorageUsageCommand(COMPANY, PROJECT, MEETING, 10_000L, 3L));

        verify(meetingStorageUsageRepository, times(2)).reportIfNewer(any());
    }

    @Test
    void getStatusReturnsNotOverQuotaWhenBelowCap() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY))
                .thenReturn(Optional.of(CompanyStoragePlan.restore(1L, COMPANY, 100_000L)));
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(50_000L);

        StorageQuotaStatusResult result = service.getStatus(COMPANY);

        assertThat(result.usedBytes()).isEqualTo(50_000L);
        assertThat(result.storageCapBytes()).isEqualTo(100_000L);
        assertThat(result.overQuota()).isFalse();
    }

    @Test
    void getStatusReturnsOverQuotaAtOrAboveCap() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY))
                .thenReturn(Optional.of(CompanyStoragePlan.restore(1L, COMPANY, 100_000L)));
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(100_000L);

        assertThat(service.getStatus(COMPANY).overQuota()).isTrue();
    }

    @Test
    void getStatusThrowsWhenPlanNotConfigured() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(COMPANY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_STORAGE_PLAN_NOT_FOUND);
    }

    @Test
    void getStatusThrowsForNullCompanyId() {
        assertThatThrownBy(() -> service.getStatus(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_FORBIDDEN_SCOPE);
    }
}
