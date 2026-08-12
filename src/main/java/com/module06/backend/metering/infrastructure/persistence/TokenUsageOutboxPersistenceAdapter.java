package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.OutboxStatus;
import com.module06.backend.metering.domain.model.TokenUsageOutbox;
import com.module06.backend.metering.domain.repository.TokenUsageOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TokenUsageOutboxPersistenceAdapter implements TokenUsageOutboxRepository {

    private final SpringDataTokenUsageOutboxRepository repository;

    public TokenUsageOutboxPersistenceAdapter(SpringDataTokenUsageOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public TokenUsageOutbox save(TokenUsageOutbox outbox) {
        return repository.saveAndFlush(TokenUsageOutboxJpaEntity.from(outbox)).toDomain();
    }

    @Override
    public boolean existsByJobId(String jobId) {
        return repository.existsByJobId(jobId);
    }

    @Override
    public List<TokenUsageOutbox> findDuePending(LocalDateTime now, int limit) {
        return repository
                .findDuePending(OutboxStatus.PENDING, now, PageRequest.of(0, limit))
                .stream()
                .map(TokenUsageOutboxJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean tryClaimForProcessing(Long id, LocalDateTime claimedUntil, LocalDateTime now) {
        // @Modifying 쿼리는 트랜잭션 안에서 실행돼야 한다.
        return repository.claimForProcessing(id, claimedUntil, now) > 0;
    }
}
