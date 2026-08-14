package com.module06.backend.metering.application.service;

import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.metering.domain.model.ProjectStorageSummary;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageOverviewServiceTest {

    private static final Long COMPANY = 7L;

    @Mock
    private MeetingStorageUsageRepository meetingStorageUsageRepository;

    @Mock
    private MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;

    @Mock
    private ProjectRepository projectRepository;

    private StorageOverviewService service;

    @BeforeEach
    void setUp() {
        service = new StorageOverviewService(meetingStorageUsageRepository, meetingTextStorageUsageRepository,
                projectRepository);
    }

    @Test
    void convertsCompanyTotalsFromBytesToGbWithOneDecimal() {
        // 1.5GB = 1,610,612,736바이트 — 소수 첫째 자리 반올림이 실제로 도는지 확인한다.
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(1_610_612_736L);
        when(meetingTextStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(0L);
        when(meetingStorageUsageRepository.summarizeByProjectId(COMPANY)).thenReturn(List.of());

        StorageOverviewResult result = service.getOverview(COMPANY);

        assertThat(result.voiceGb()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(result.sttGb()).isEqualByComparingTo(BigDecimal.ZERO.setScale(1));
    }

    @Test
    void excludesProjectsWithoutAnyRemainingRecording() {
        // meetingCount=0(전부 삭제됨)인 프로젝트는 목록에서 빠져야 한다 — 지울 것도 볼 것도 없어서.
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(0L);
        when(meetingTextStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(0L);
        when(meetingStorageUsageRepository.summarizeByProjectId(COMPANY)).thenReturn(List.of(
                new ProjectStorageSummary(1L, 0L, 0L, null)));

        StorageOverviewResult result = service.getOverview(COMPANY);

        assertThat(result.projects()).isEmpty();
    }

    @Test
    void joinsProjectMetadataAndDefaultsMissingTextBytesToZero() {
        LocalDateTime lastRecordedAt = LocalDateTime.of(2026, 8, 10, 9, 30);
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(1_073_741_824L);
        when(meetingTextStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(0L);
        when(meetingStorageUsageRepository.summarizeByProjectId(COMPANY)).thenReturn(List.of(
                new ProjectStorageSummary(101L, 1_073_741_824L, 3L, lastRecordedAt)));
        // 이 프로젝트는 자막·요약을 한 번도 리포트한 적이 없다 — 맵에 키 자체가 없다.
        when(meetingTextStorageUsageRepository.sumUsedBytesGroupedByProjectId(COMPANY)).thenReturn(Map.of());
        when(projectRepository.findAllByCompanyIdAndIdIn(eq(COMPANY), any()))
                .thenReturn(List.of(project(101L, "eng", "엔지니어링", ProjectStatus.DONE)));

        StorageOverviewResult result = service.getOverview(COMPANY);

        assertThat(result.projects()).hasSize(1);
        StorageOverviewResult.ProjectStorageItem item = result.projects().get(0);
        assertThat(item.tag()).isEqualTo("eng");
        assertThat(item.name()).isEqualTo("엔지니어링");
        assertThat(item.meetingCount()).isEqualTo(3L);
        assertThat(item.voiceGb()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(item.sttGb()).isEqualByComparingTo(BigDecimal.ZERO.setScale(1));
        assertThat(item.lastRecordedAt()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(item.status()).isEqualTo(ProjectStatus.DONE);
    }

    @Test
    void skipsProjectsMissingFromProjectRepositoryLookup() {
        // summarizeByProjectId는 101L을 돌려줬는데, 그 사이 프로젝트가 지워져 조회에서 안 나온 경우.
        when(meetingStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(0L);
        when(meetingTextStorageUsageRepository.sumUsedBytesByCompanyId(COMPANY)).thenReturn(0L);
        when(meetingStorageUsageRepository.summarizeByProjectId(COMPANY)).thenReturn(List.of(
                new ProjectStorageSummary(101L, 500L, 1L, LocalDateTime.now())));
        when(meetingTextStorageUsageRepository.sumUsedBytesGroupedByProjectId(COMPANY)).thenReturn(Map.of());
        when(projectRepository.findAllByCompanyIdAndIdIn(eq(COMPANY), any())).thenReturn(List.of());

        StorageOverviewResult result = service.getOverview(COMPANY);

        assertThat(result.projects()).isEmpty();
    }

    private Project project(Long id, String tag, String name, ProjectStatus status) {
        return Project.reconstitute(id, COMPANY, tag, name, "desc", "#6B7280", status,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1L, List.of(),
                null, LocalDateTime.now(), LocalDateTime.now());
    }
}
