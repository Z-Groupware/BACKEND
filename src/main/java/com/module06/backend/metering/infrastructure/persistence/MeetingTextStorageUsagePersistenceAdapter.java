package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingTextStorageUsage;
import com.module06.backend.metering.domain.model.TextStorageSource;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class MeetingTextStorageUsagePersistenceAdapter implements MeetingTextStorageUsageRepository {

    private final SpringDataMeetingTextStorageUsageRepository repository;
    private final MeetingTextStorageUsageWriter writer;
    private final Clock clock;

    public MeetingTextStorageUsagePersistenceAdapter(SpringDataMeetingTextStorageUsageRepository repository,
                                                      MeetingTextStorageUsageWriter writer,
                                                      @Qualifier("meetingClock") Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.clock = clock;
    }

    /*
     * MeetingStorageUsagePersistenceAdapter.reportIfNewer와 동일한 재시도 패턴 — 첫 report 두
     * 개가 동시에 경합해 삽입이 충돌하면(DataIntegrityViolationException) writer의 트랜잭션은
     * 이미 롤백된 상태로 예외가 여기(트랜잭션 밖)까지 전파된다. 여기서 다시 writer를 부르면
     * (새 트랜잭션) 이번엔 방금 생긴 행을 잠그는 경로를 타 안전하게 재시도된다.
     */
    @Override
    public void reportIfNewer(Long meetingId, Long companyId, Long projectId, TextStorageSource source,
                              long usedBytes, long revision, LocalDateTime updatedAt) {
        try {
            writer.applyWithLock(meetingId, companyId, projectId, source, usedBytes, revision, updatedAt);
        } catch (DataIntegrityViolationException e) {
            writer.applyWithLock(meetingId, companyId, projectId, source, usedBytes, revision, updatedAt);
        }
    }

    @Override
    public void clearByCompanyIdAndProjectId(Long companyId, Long projectId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MeetingTextStorageUsageJpaEntity> cleared = repository.findByCompanyId(companyId).stream()
                .map(MeetingTextStorageUsageJpaEntity::toDomain)
                .filter(usage -> usage.getProjectId().equals(projectId))
                .map(usage -> MeetingTextStorageUsage.restore(usage.getMeetingId(), usage.getCompanyId(),
                        usage.getProjectId(), 0L, usage.getCaptionRevision() + 1,
                        0L, usage.getTranscriptRevision() + 1, 0L, usage.getSummaryRevision() + 1, now))
                .map(MeetingTextStorageUsageJpaEntity::from)
                .toList();
        repository.saveAll(cleared);
    }

    @Override
    public long sumUsedBytesByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).stream()
                .mapToLong(entity -> entity.toDomain().getTotalUsedBytes())
                .sum();
    }

    // QUERY_002(CI Gate 1, 신규 @Query 금지) 준수 — MeetingStorageUsagePersistenceAdapter와 동일한
    // "이미 있는 findByCompanyId + Java 집계" 패턴.
    @Override
    public Map<Long, Long> sumUsedBytesGroupedByProjectId(Long companyId) {
        return repository.findByCompanyId(companyId).stream()
                .map(MeetingTextStorageUsageJpaEntity::toDomain)
                .collect(Collectors.groupingBy(MeetingTextStorageUsage::getProjectId,
                        Collectors.summingLong(MeetingTextStorageUsage::getTotalUsedBytes)));
    }
}
