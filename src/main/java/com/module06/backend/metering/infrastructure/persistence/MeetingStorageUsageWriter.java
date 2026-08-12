package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/*
 * MeetingStorageUsagePersistenceAdapter.reportIfNewer의 실제 쓰기를 별도 빈으로 뺐다 — 같은
 * 클래스 안에서 메서드를 호출하면(self-invocation) @Transactional이 프록시를 안 타 무시된다
 * (NotificationInsertWriter·SttBlockFormedWriter와 동일한 이유의 분리).
 *
 * applyWithLock 하나로 "이미 있으면 잠그고 revision 비교 후 갱신" · "없으면 삽입"을 모두 처리한다.
 * 삽입이 동시 경합으로 실패하면(DataIntegrityViolationException) 이 트랜잭션은 그대로 롤백되고,
 * 예외가 이 메서드 밖(어댑터의 비-트랜잭션 코드)으로 전파된다 — 호출자가 그걸 잡고 이 메서드를
 * 다시 부르면(새 트랜잭션) 이번엔 방금 생긴 행을 잠그는 경로를 탄다. 트랜잭션 안에서 예외를
 * 잡고 계속하지 않으므로 UnexpectedRollbackException 함정이 없다.
 *
 * REQUIRES_NEW인 이유 — 이 writer의 호출자(cap의 DeleteRecordingService·ManualRecordingService
 * 등)는 클래스 레벨 @Transactional로 이미 트랜잭션을 열어둔 상태에서 이 메서드를 부른다. 기본
 * REQUIRED로 두면 이 메서드의 트랜잭션이 호출자 트랜잭션에 그대로 합류해서, 삽입 경합 예외가
 * 나면(바로 위 문단) 호출자 트랜잭션 전체가 rollback-only로 마킹된다 — 여기서 예외를 잡고
 * 재시도해도 호출자가 나중에 커밋할 때 UnexpectedRollbackException으로 죽는다. 독립된 새
 * 트랜잭션으로 분리해야 이 메서드 안의 실패가 호출자 트랜잭션을 오염시키지 않는다.
 */
@Component
class MeetingStorageUsageWriter {

    private final SpringDataMeetingStorageUsageRepository repository;

    MeetingStorageUsageWriter(SpringDataMeetingStorageUsageRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void applyWithLock(MeetingStorageUsage usage) {
        Optional<MeetingStorageUsageJpaEntity> locked = repository.findWithLockByMeetingId(usage.getMeetingId());
        if (locked.isPresent()) {
            if (usage.isNewerThan(locked.get().toDomain())) {
                repository.save(MeetingStorageUsageJpaEntity.from(usage));
            }
            return;
        }
        repository.saveAndFlush(MeetingStorageUsageJpaEntity.from(usage));
    }
}
