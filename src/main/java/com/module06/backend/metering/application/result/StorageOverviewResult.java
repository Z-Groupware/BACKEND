package com.module06.backend.metering.application.result;

import com.module06.backend.project.domain.model.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 저장소 관리 화면(/manage/storage)의 조회 결과. 회사 총량(voiceGb/sttGb)은 프로젝트 목록 필터와
 * 무관하게 회사 전체 SUM이다 — 구독 화면(billing)의 사용량 지표와 같은 원천·같은 값이어야 한다.
 *
 * projects는 "지금 녹음이 남아있는(meetingCount > 0)" 프로젝트만 담는다 — 녹음이 하나도 없으면
 * 지울 것도 볼 것도 없다(StorageOverviewService 참고).
 */
public record StorageOverviewResult(BigDecimal voiceGb, BigDecimal sttGb, List<ProjectStorageItem> projects) {

    public record ProjectStorageItem(String tag, String name, long meetingCount, BigDecimal voiceGb,
                                      BigDecimal sttGb, LocalDate lastRecordedAt, ProjectStatus status) {
    }
}
