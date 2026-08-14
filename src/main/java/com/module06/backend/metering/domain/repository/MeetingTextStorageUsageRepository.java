package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.TextStorageSource;

import java.time.LocalDateTime;
import java.util.Map;

public interface MeetingTextStorageUsageRepository {

    /**
     * 한 소스(캡션/transcript/요약)의 리포트를 반영한다. meetingId가 식별자다 — 있으면 그 소스의
     * 컬럼만 갱신(revision이 기존 저장값보다 크지 않으면 조용히 무시), 없으면 새로 생성한다.
     */
    void reportIfNewer(Long meetingId, Long companyId, Long projectId, TextStorageSource source, long usedBytes,
                       long revision, LocalDateTime updatedAt);

    /** 이 회사의 모든 회의 스냅샷을 합산한(세 소스 총합) 현재 총 자막·요약 사용량. */
    long sumUsedBytesByCompanyId(Long companyId);

    /** 이 회사의 모든 회의 스냅샷을 project_id로 묶어(세 소스 총합) 프로젝트별 자막·요약 사용량을 낸다. */
    Map<Long, Long> sumUsedBytesGroupedByProjectId(Long companyId);
}
