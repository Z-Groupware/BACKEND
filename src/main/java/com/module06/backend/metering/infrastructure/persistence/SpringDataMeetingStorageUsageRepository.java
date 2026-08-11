package com.module06.backend.metering.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface SpringDataMeetingStorageUsageRepository extends JpaRepository<MeetingStorageUsageJpaEntity, Long> {

    List<MeetingStorageUsageJpaEntity> findByCompanyId(Long companyId);

    // revision 비교(reportIfNewer)를 잠금 없이 하면 동시 report 두 개가 같은 "기존값"을 읽고 둘 다
    // 반영을 결정해버릴 수 있다 — CaptureUploadStateRepository와 동일한 비관적 락 CAS 패턴.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingStorageUsageJpaEntity> findWithLockByMeetingId(Long meetingId);
}
