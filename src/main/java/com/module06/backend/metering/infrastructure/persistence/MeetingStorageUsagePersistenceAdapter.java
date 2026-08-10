package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingStorageUsagePersistenceAdapter implements MeetingStorageUsageRepository {

    private final SpringDataMeetingStorageUsageRepository repository;

    public MeetingStorageUsagePersistenceAdapter(SpringDataMeetingStorageUsageRepository repository) {
        this.repository = repository;
    }

    @Override
    public MeetingStorageUsage save(MeetingStorageUsage usage) {
        return repository.save(MeetingStorageUsageJpaEntity.from(usage)).toDomain();
    }

    // 회의 수만큼만 행이 있어서(TokenUsageRecord와 달리 이벤트마다 안 늘어남) Java 쪽에서 합산해도
    // TokenUsageRecordPersistenceAdapter.sumTotalTokens와 같은 비용 구조다.
    @Override
    public long sumUsedBytesByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).stream()
                .mapToLong(entity -> entity.toDomain().getUsedBytes())
                .sum();
    }
}
