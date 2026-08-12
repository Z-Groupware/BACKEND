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
     * claimed_until 이 만료된(또는 null) 항목만 반환한다.
     */
    List<TokenUsageOutbox> findDuePending(LocalDateTime now, int limit);

    /**
     * 항목을 원자적으로 클레임한다. 조건부 UPDATE 로 경쟁 인스턴스 중 하나만 성공한다.
     *
     * @param id           클레임할 항목 ID
     * @param claimedUntil 리스 만료 시각 (이 시각이 지나면 다른 인스턴스가 재획득 가능)
     * @param now          현재 시각 (만료 여부 판정에 사용)
     * @return 클레임 성공 여부 (false = 다른 인스턴스가 이미 가져간 것 → 건너뜀)
     */
    boolean tryClaimForProcessing(Long id, LocalDateTime claimedUntil, LocalDateTime now);
}
