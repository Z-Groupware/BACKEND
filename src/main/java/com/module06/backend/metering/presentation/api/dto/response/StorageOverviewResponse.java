package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.project.domain.model.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// FE storage/types.ts의 StorageOverview 계약과 필드명을 그대로 맞춘다 — tag가 식별자다(projectId 없음,
// 화면이 /app/projects/:tag로 이동·삭제 액션 모두 tag로 부른다).
public record StorageOverviewResponse(BigDecimal voiceGb, BigDecimal sttGb, List<ProjectStorageResponse> projects) {

    public record ProjectStorageResponse(String tag, String name, long meetingCount, BigDecimal voiceGb,
                                         BigDecimal sttGb, LocalDate lastRecordedAt, ProjectStatus status) {

        static ProjectStorageResponse from(StorageOverviewResult.ProjectStorageItem item) {
            return new ProjectStorageResponse(item.tag(), item.name(), item.meetingCount(), item.voiceGb(),
                    item.sttGb(), item.lastRecordedAt(), item.status());
        }
    }

    public static StorageOverviewResponse from(StorageOverviewResult result) {
        return new StorageOverviewResponse(result.voiceGb(), result.sttGb(),
                result.projects().stream().map(ProjectStorageResponse::from).toList());
    }
}
