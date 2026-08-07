package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataTokenUsageRecordRepository extends JpaRepository<TokenUsageRecordJpaEntity, Long> {

    boolean existsByJobId(String jobId);

    @Query("""
            select coalesce(sum(r.totalTokens), 0)
            from TokenUsageRecordJpaEntity r
            where r.companyId = :companyId
              and r.recordedAt >= :start
              and r.recordedAt < :end
            """)
    long sumTotalTokens(@Param("companyId") Long companyId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(r.totalTokens), 0)
            from TokenUsageRecordJpaEntity r
            where r.companyId = :companyId
              and r.teamId = :teamId
              and r.recordedAt >= :start
              and r.recordedAt < :end
            """)
    long sumTotalTokensByTeam(@Param("companyId") Long companyId,
                              @Param("teamId") Long teamId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    @Query("""
            select new com.module06.backend.metering.infrastructure.persistence.TeamUsageRow(
                r.teamId,
                coalesce(sum(r.totalTokens), 0)
            )
            from TokenUsageRecordJpaEntity r
            where r.companyId = :companyId
              and r.recordedAt >= :start
              and r.recordedAt < :end
            group by r.teamId
            order by r.teamId
            """)
    List<TeamUsageRow> sumTotalTokensByDepartment(@Param("companyId") Long companyId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);
}
