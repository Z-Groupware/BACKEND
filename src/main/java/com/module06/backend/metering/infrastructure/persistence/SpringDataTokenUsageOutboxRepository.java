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

    // TENANT_001 승인: job_id 는 token_usage_outbox 에 전역 UNIQUE 다
    // (V7.12 uq_token_usage_outbox_job_id) — 회사와 무관하게 최대 한 행이므로 회사 조건을
    // 더해도 결과가 같다. 오히려 회사로 스코프하면 같은 jobId 가 다른 회사로 한 번 더
    // 적재되려다 INSERT 시점에 UNIQUE 위반으로 터진다. 멱등 키 조회는 전역이어야 맞다.
    // nosemgrep: review-loop.semgrep.tenant-derived-query-without-company-scope
    boolean existsByJobId(String jobId);

    /**
     * 재시도할 때가 된 PENDING 항목을 가져온다.
     *
     * claimed_until 이 null(미클레임) 이거나 만료된 항목만 포함한다 — 다른 릴레이 인스턴스가
     * 현재 처리 중인 항목(claimed_until > now)은 건너뛴다.
     */
    // QUERY_002 승인: (claimedUntil IS NULL OR claimedUntil < :now) 처럼 같은 컬럼에 대한
    // IS NULL OR 비교 조건은 단일 파생 쿼리 메서드로 표현할 수 없어 @Query 가 불가피하다.
    // nosemgrep: no-new-jpa-query-annotation
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
    // QUERY_002 승인: 원자적 클레임을 위한 @Modifying UPDATE 는 파생 쿼리 메서드로 표현할 수 없다.
    // nosemgrep: no-new-jpa-query-annotation
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
