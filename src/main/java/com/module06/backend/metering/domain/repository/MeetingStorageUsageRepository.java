package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.model.ProjectStorageSummary;

import java.util.List;

public interface MeetingStorageUsageRepository {

    /**
     * meetingId가 식별자다 — 있으면 갱신, 없으면 새로 생성한다. revision이 기존 저장값보다 크지
     * 않으면(뒤바뀐 순서로 도착했거나 중복) 조용히 무시한다 — 항상 덮어쓰면 오래된 report가 최신
     * 값을 지워 사용량이 과소 집계될 수 있다.
     */
    void reportIfNewer(MeetingStorageUsage usage);

    /** 이 회사의 모든 회의 스냅샷을 합산한 현재 총 사용량. */
    long sumUsedBytesByCompanyId(Long companyId);

    /**
     * 이 회사의 모든 회의 스냅샷을 project_id로 묶어 프로젝트별 음성 사용량·회의 수·마지막 녹음
     * 일시를 낸다(저장소 관리 화면의 프로젝트별 내역용).
     */
    List<ProjectStorageSummary> summarizeByProjectId(Long companyId);
}
