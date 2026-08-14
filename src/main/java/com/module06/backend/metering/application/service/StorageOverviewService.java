package com.module06.backend.metering.application.service;

import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.metering.application.result.StorageOverviewResult.ProjectStorageItem;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import com.module06.backend.metering.domain.model.ProjectStorageSummary;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 저장소 관리 화면(/manage/storage) 조회 — Commit 1~5에서 만든 음성·자막 프로젝트별 집계
// (MeetingStorageUsageRepository/MeetingTextStorageUsageRepository)를 project 도메인의
// tag·name·status와 엮어 화면이 바로 쓸 수 있는 모양으로 조립한다.
@Service
public class StorageOverviewService implements GetStorageOverviewUseCase {

    // FE 화면(storage/types.ts)이 GB 숫자로 표시하는 소수 자릿수 — 목업이 "34.9GB"처럼 소수점 1자리.
    private static final int GB_SCALE = 1;
    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024 * 1024);

    private final MeetingStorageUsageRepository meetingStorageUsageRepository;
    private final MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;
    private final ProjectRepository projectRepository;

    public StorageOverviewService(MeetingStorageUsageRepository meetingStorageUsageRepository,
                                  MeetingTextStorageUsageRepository meetingTextStorageUsageRepository,
                                  ProjectRepository projectRepository) {
        this.meetingStorageUsageRepository = meetingStorageUsageRepository;
        this.meetingTextStorageUsageRepository = meetingTextStorageUsageRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StorageOverviewResult getOverview(Long companyId) {
        long totalVoiceBytes = meetingStorageUsageRepository.sumUsedBytesByCompanyId(companyId);
        long totalTextBytes = meetingTextStorageUsageRepository.sumUsedBytesByCompanyId(companyId);

        List<ProjectStorageItem> projects = buildProjectItems(companyId);

        return new StorageOverviewResult(toGb(totalVoiceBytes), toGb(totalTextBytes), projects);
    }

    private List<ProjectStorageItem> buildProjectItems(Long companyId) {
        // "지금 녹음이 남아있는" 프로젝트만 화면에 보여준다(meetingCount > 0) — 녹음이 하나도
        // 없으면(전부 삭제됐거나 애초에 없었으면) 지울 것도 볼 것도 없다.
        List<ProjectStorageSummary> withRecordings = meetingStorageUsageRepository.summarizeByProjectId(companyId)
                .stream()
                .filter(summary -> summary.meetingCount() > 0)
                .toList();
        if (withRecordings.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> textBytesByProject = meetingTextStorageUsageRepository
                .sumUsedBytesGroupedByProjectId(companyId);
        Map<Long, Project> projectsById = projectRepository
                .findAllByCompanyIdAndIdIn(companyId, withRecordings.stream()
                        .map(ProjectStorageSummary::projectId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));

        return withRecordings.stream()
                // 집계 조회와 프로젝트 조회 사이에 프로젝트가 지워졌으면(드묾) 그 줄은 건너뛴다.
                .filter(summary -> projectsById.containsKey(summary.projectId()))
                .map(summary -> toProjectItem(summary, projectsById.get(summary.projectId()),
                        textBytesByProject.getOrDefault(summary.projectId(), 0L)))
                .toList();
    }

    private ProjectStorageItem toProjectItem(ProjectStorageSummary summary, Project project, long textBytes) {
        return new ProjectStorageItem(project.getTag(), project.getName(), summary.meetingCount(),
                toGb(summary.usedBytes()), toGb(textBytes), summary.lastRecordedAt().toLocalDate(),
                project.getStatus());
    }

    private BigDecimal toGb(long bytes) {
        return BigDecimal.valueOf(bytes).divide(BYTES_PER_GB, GB_SCALE, RoundingMode.HALF_UP);
    }
}
