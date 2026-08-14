package com.module06.backend.metering.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface SpringDataMeetingTextStorageUsageRepository
        extends JpaRepository<MeetingTextStorageUsageJpaEntity, Long> {

    List<MeetingTextStorageUsageJpaEntity> findByCompanyId(Long companyId);

    /*
     * TENANT_001 예외: meetingId가 이 테이블의 PK라 한 회의 = 한 회사로 이미 유일하게 좁혀진다.
     * 호출자(MeetingTextStorageUsageWriter.applyWithLock)는 report 커맨드에 실린 companyId·
     * meetingId 쌍을 그대로 쓸 뿐, meetingId만으로 다른 회사 행을 찾아 나설 방법이 없다 — 목록
     * 조회가 아니라 이미 식별된 단일 행을 잠그는 쓰기 경로다(SpringDataHandoverRefRepository의
     * TENANT_001 예외와 동일한 논리).
     *
     * revision 비교(reportIfNewer)를 잠금 없이 하면 동시 report 두 개가 같은 "기존값"을 읽고 둘 다
     * 반영을 결정해버릴 수 있다 — SpringDataMeetingStorageUsageRepository와 동일한 비관적 락 CAS 패턴.
     */
    // nosemgrep: tenant-derived-query-without-company-scope
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingTextStorageUsageJpaEntity> findWithLockByMeetingId(Long meetingId);
}
