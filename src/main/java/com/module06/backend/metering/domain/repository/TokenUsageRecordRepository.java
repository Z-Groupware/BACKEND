package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.TokenUsageRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageRecordRepository {

    TokenUsageRecord save(TokenUsageRecord record);

    boolean existsByJobId(String jobId);

    long sumTotalTokens(Long companyId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    long sumTotalTokensByTeam(Long companyId, Long teamId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    List<DepartmentUsageAggregate> sumTotalTokensByDepartment(Long companyId, LocalDateTime startInclusive,
                                                              LocalDateTime endExclusive);

    record DepartmentUsageAggregate(Long teamId, long usedTokens) {
    }
}
