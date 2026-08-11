package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.TokenUsageOutbox;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageOutboxRepository {

    TokenUsageOutbox save(TokenUsageOutbox outbox);

    boolean existsByJobId(String jobId);

    /**
     * 재시도할 때가 된 PENDING 항목을 nextAttemptAt 오름차순으로 가져온다(오래 밀린 것부터).
     * limit 로 한 번에 처리하는 양을 제한해 릴레이가 폭주하지 않게 한다.
     */
    List<TokenUsageOutbox> findDuePending(LocalDateTime now, int limit);
}
