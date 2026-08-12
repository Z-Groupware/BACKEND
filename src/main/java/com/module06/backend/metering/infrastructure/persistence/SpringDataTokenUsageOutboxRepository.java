package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataTokenUsageOutboxRepository extends JpaRepository<TokenUsageOutboxJpaEntity, Long> {

    boolean existsByJobId(String jobId);

    /**
     * 재시도할 때가 된 PENDING 항목을 가져온다.
     *
     * claimed_until 이 null(미클레임) 이거나 만료된 항목만 포함한다 — 다른 릴레이 인스턴스가
     * 현재 처리 중인 항목(claimed_until > now)은 건너뛴다.
     */
    @Query("SELECT e FROM TokenUsageOutboxJpaEntity e " +
           "WHERE e.status = :status " +
           "AND e.nextAttemptAt <= :now " +
           "AND (e.claimedUntil IS NULL OR e.claimedUntil < :now) " +
           "ORDER BY e.nextAttemptAt ASC")
    List<TokenUsageOutboxJpaEntity> findDuePending(
            @Param("status") OutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * 특정 항목을 원자적으로 클레임한다.
     *
     * WHERE 조건: status = PENDING AND (claimed_until IS NULL OR claimed_until < now).
     * 업데이트 행 수 = 1이면 클레임 성공, 0이면 다른 인스턴스가 이미 가져간 것 → 건너뜀.
     *
     * @return 업데이트된 행 수 (1=성공, 0=이미 클레임됨)
     */
    @Modifying
    @Query("UPDATE TokenUsageOutboxJpaEntity e " +
           "SET e.claimedUntil = :claimedUntil " +
           "WHERE e.id = :id " +
           "AND e.status = com.module06.backend.metering.domain.model.OutboxStatus.PENDING " +
           "AND (e.claimedUntil IS NULL OR e.claimedUntil < :now)")
    int claimForProcessing(
            @Param("id") Long id,
            @Param("claimedUntil") LocalDateTime claimedUntil,
            @Param("now") LocalDateTime now);
}
