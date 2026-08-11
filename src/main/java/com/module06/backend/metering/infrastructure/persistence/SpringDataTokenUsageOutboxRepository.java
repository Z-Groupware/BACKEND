package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataTokenUsageOutboxRepository extends JpaRepository<TokenUsageOutboxJpaEntity, Long> {

    boolean existsByJobId(String jobId);

    List<TokenUsageOutboxJpaEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            OutboxStatus status, LocalDateTime now, Pageable pageable);
}
