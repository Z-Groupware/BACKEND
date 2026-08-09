package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataTokenUsageRecordRepository extends JpaRepository<TokenUsageRecordJpaEntity, Long> {

    boolean existsByJobId(String jobId);

    List<TokenUsageRecordJpaEntity> findByCompanyIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
            Long companyId, LocalDateTime start, LocalDateTime end);

    List<TokenUsageRecordJpaEntity> findByCompanyIdAndTeamIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
            Long companyId, Long teamId, LocalDateTime start, LocalDateTime end);
}
