package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataMeetingStorageUsageRepository extends JpaRepository<MeetingStorageUsageJpaEntity, Long> {

    List<MeetingStorageUsageJpaEntity> findByCompanyId(Long companyId);
}
