package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.TokenUsageRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageRecordRepository {

    TokenUsageRecord save(TokenUsageRecord record);

    boolean existsByJobId(String jobId);

    long sumTotalTokens(Long companyId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    long sumTotalTokensByTeam(Long companyId, Long teamId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    /** 회사 전체의 방향별(입력·출력) 토큰 합계. 방향 단가 과금의 기반. */
    DirectionUsageAggregate sumDirectionTokens(Long companyId, LocalDateTime startInclusive,
                                               LocalDateTime endExclusive);

    List<DepartmentUsageAggregate> sumTotalTokensByDepartment(Long companyId, LocalDateTime startInclusive,
                                                              LocalDateTime endExclusive);

    record DepartmentUsageAggregate(Long teamId, long usedTokens, long inputTokens, long outputTokens) {
    }

    record DirectionUsageAggregate(long inputTokens, long outputTokens) {

        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
