package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingTextStorageUsage;
import com.module06.backend.metering.domain.model.TextStorageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/*
 * MeetingTextStorageUsagePersistenceAdapter.reportIfNewer의 실제 쓰기를 별도 빈으로 뺐다 —
 * MeetingStorageUsageWriter와 동일한 이유(self-invocation이면 @Transactional 프록시를 안 탄다).
 *
 * REQUIRES_NEW인 이유도 동일하다 — 호출자(cap의 SubmitCaptionsService, capture의 STT/요약 저장
 * 서비스)가 이미 트랜잭션을 열어둔 상태에서 이 메서드를 부르므로, 여기서 삽입 경합 예외가 나도
 * 호출자 트랜잭션이 rollback-only로 오염되지 않도록 독립된 새 트랜잭션으로 분리한다.
 *
 * 행이 이미 있으면 그 소스 컬럼만 병합(MeetingTextStorageUsage.withSourceReportIfNewer)하고,
 * 없으면 첫 리포트로 새로 만든다(MeetingTextStorageUsage.firstReport) — 두 producer가 같은
 * 회의에 각자 리포트해도 서로의 컬럼을 건드리지 않는다.
 */
@Component
class MeetingTextStorageUsageWriter {

    private final SpringDataMeetingTextStorageUsageRepository repository;

    MeetingTextStorageUsageWriter(SpringDataMeetingTextStorageUsageRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void applyWithLock(Long meetingId, Long companyId, Long projectId, TextStorageSource source, long usedBytes,
                       long revision, LocalDateTime updatedAt) {
        Optional<MeetingTextStorageUsageJpaEntity> locked = repository.findWithLockByMeetingId(meetingId);
        if (locked.isEmpty()) {
            repository.saveAndFlush(MeetingTextStorageUsageJpaEntity.from(
                    MeetingTextStorageUsage.firstReport(meetingId, companyId, projectId, source, usedBytes,
                            revision, updatedAt)));
            return;
        }
        MeetingTextStorageUsage current = locked.get().toDomain();
        MeetingTextStorageUsage merged = current.withSourceReportIfNewer(source, usedBytes, revision, updatedAt);
        if (merged != current) {
            repository.save(MeetingTextStorageUsageJpaEntity.from(merged));
        }
    }
}
