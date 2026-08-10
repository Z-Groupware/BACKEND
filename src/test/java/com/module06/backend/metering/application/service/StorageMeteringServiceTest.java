package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.result.StorageQuotaStatusResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.model.QuotaStatus;
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
    void reportSavesSnapshotWithCurrentTimestamp() {
        when(meetingStorageUsageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.report(new ReportMeetingStorageUsageCommand(COMPANY, MEETING, 12_345L));

        ArgumentCaptor<MeetingStorageUsage> captor = ArgumentCaptor.forClass(MeetingStorageUsage.class);
        verify(meetingStorageUsageRepository).save(captor.capture());
        MeetingStorageUsage saved = captor.getValue();
        assertThat(saved.getMeetingId()).isEqualTo(MEETING);
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY);
        assertThat(saved.getUsedBytes()).isEqualTo(12_345L);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void reportingSameValueTwiceIsIdempotent() {
        when(meetingStorageUsageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.report(new ReportMeetingStorageUsageCommand(COMPANY, MEETING, 12_345L));
        service.report(new ReportMeetingStorageUsageCommand(COMPANY, MEETING, 12_345L));

        // meetingId가 식별자라 두 번째 호출도 그냥 같은 값으로 덮어쓴다 — 예외 없이 두 번 다 save된다.
        verify(meetingStorageUsageRepository, times(2)).save(any());
    }

    @Test
    void getStatusComputesQuotaFromSumOfMeetingSnapshots() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY))
                .thenReturn(Optional.of(CompanyStoragePlan.restore(1L, COMPANY, 100_000L)));
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(50_000L);

        StorageQuotaStatusResult result = service.getStatus(COMPANY);

        assertThat(result.usedBytes()).isEqualTo(50_000L);
        assertThat(result.storageCapBytes()).isEqualTo(100_000L);
        assertThat(result.quotaStatus()).isEqualTo(QuotaStatus.WITHIN);
    }

    @Test
    void getStatusReturnsSoftWarnAt80Percent() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY))
                .thenReturn(Optional.of(CompanyStoragePlan.restore(1L, COMPANY, 100_000L)));
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(85_000L);

        assertThat(service.getStatus(COMPANY).quotaStatus()).isEqualTo(QuotaStatus.SOFT_WARN);
    }

    @Test
    void getStatusReturnsOverAtOrAboveCap() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY))
                .thenReturn(Optional.of(CompanyStoragePlan.restore(1L, COMPANY, 100_000L)));
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(100_000L);

        assertThat(service.getStatus(COMPANY).quotaStatus()).isEqualTo(QuotaStatus.OVER);
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
