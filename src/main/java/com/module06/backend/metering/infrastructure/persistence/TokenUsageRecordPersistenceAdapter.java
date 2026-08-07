package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TokenUsageRecordPersistenceAdapter implements TokenUsageRecordRepository {

    private final SpringDataTokenUsageRecordRepository repository;

    public TokenUsageRecordPersistenceAdapter(SpringDataTokenUsageRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public TokenUsageRecord save(TokenUsageRecord record) {
        return repository.saveAndFlush(TokenUsageRecordJpaEntity.from(record)).toDomain();
    }

    @Override
    public boolean existsByJobId(String jobId) {
        return repository.existsByJobId(jobId);
    }

    @Override
    public long sumTotalTokens(Long companyId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return repository.sumTotalTokens(companyId, startInclusive, endExclusive);
    }

    @Override
    public long sumTotalTokensByTeam(Long companyId, Long teamId, LocalDateTime startInclusive,
                                     LocalDateTime endExclusive) {
        return repository.sumTotalTokensByTeam(companyId, teamId, startInclusive, endExclusive);
    }

    @Override
    public List<DepartmentUsageAggregate> sumTotalTokensByDepartment(Long companyId, LocalDateTime startInclusive,
                                                                    LocalDateTime endExclusive) {
        return repository.sumTotalTokensByDepartment(companyId, startInclusive, endExclusive).stream()
                .map(row -> new DepartmentUsageAggregate(row.teamId(), row.usedTokens()))
                .toList();
    }
}
