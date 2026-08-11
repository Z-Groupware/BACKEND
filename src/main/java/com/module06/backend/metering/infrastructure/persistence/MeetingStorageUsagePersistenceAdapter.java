package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingStorageUsagePersistenceAdapter implements MeetingStorageUsageRepository {

    private final SpringDataMeetingStorageUsageRepository repository;
    private final MeetingStorageUsageWriter writer;

    public MeetingStorageUsagePersistenceAdapter(SpringDataMeetingStorageUsageRepository repository,
                                                 MeetingStorageUsageWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    /*
     * 이 메서드 자체는 @Transactional이 아니다 — 실제 잠금·쓰기는 writer.applyWithLock(별도 빈,
     * 자체 트랜잭션)이 담당한다. 첫 report 두 개가 동시에 경합해 삽입이 충돌하면
     * (DataIntegrityViolationException) writer의 트랜잭션은 이미 깨끗하게 롤백된 상태로 예외가
     * 여기(트랜잭션 밖)까지 전파된다 — 여기서 다시 writer를 호출하면(새 트랜잭션) 이번엔 방금
     * 생긴 행을 잠그는 경로를 타 안전하게 재시도된다.
     */
    @Override
    public void reportIfNewer(MeetingStorageUsage usage) {
        try {
            writer.applyWithLock(usage);
        } catch (DataIntegrityViolationException e) {
            writer.applyWithLock(usage);
        }
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
