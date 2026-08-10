package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;

public interface MeetingStorageUsageRepository {

    /** meetingId가 식별자다 — 있으면 갱신, 없으면 새로 생성(멱등, 같은 값을 다시 report해도 안전). */
    MeetingStorageUsage save(MeetingStorageUsage usage);

    /** 이 회사의 모든 회의 스냅샷을 합산한 현재 총 사용량. */
    long sumUsedBytesByCompanyId(Long companyId);
}
