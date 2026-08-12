package com.module06.backend.metering.infrastructure.scheduling;

import com.module06.backend.metering.application.service.TokenUsageLedgerAppender;
import com.module06.backend.metering.domain.model.TokenUsageOutbox;
import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.TokenUsageOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 원장 반영에 실패해 outbox 에 쌓인 토큰 사용량을 재시도로 흘려보내는 릴레이(보장된 전달).
 *
 * <h2>왜 안전한가</h2>
 * 원장은 job_id UNIQUE 로 멱등하다 — 같은 항목을 몇 번 재적용해도 원장은 한 줄만 남는다.
 * 그래서 at-least-once 재시도가 중복 과금을 만들지 않는다. 재시도해도 계속 실패하면 지수
 * 백오프로 뒤로 밀고, {@link #MAX_ATTEMPTS}회를 넘기면 DEAD(dead-letter)로 남겨 자동 복구를
 * 포기하되 <b>조용히 사라지지는 않게</b> 한다(로그·상태로 관측 가능).
 *
 * <h2>@EnableScheduling</h2>
 * 앱 어딘가(capture·notification 스케줄러)에서 이미 켜져 있어 여기서 다시 켜지 않는다.
 */
@Component
public class TokenUsageRelay {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageRelay.class);

    // 한 번에 처리하는 항목 수. 릴레이가 한 틱에 DB를 통째로 훑지 않게 상한을 둔다.
    private static final int BATCH_SIZE = 100;
    // 이 횟수를 넘기면 dead-letter. 일시적 순단은 몇 번의 재시도로 회복되고, 그 이상은 사람이 봐야 한다.
    static final int MAX_ATTEMPTS = 5;
    private static final long BASE_BACKOFF_SECONDS = 30L;
    // 릴레이 인스턴스가 항목을 처리하는 데 허용하는 최대 시간. 이 시간 안에 완료하지 못하면
    // 리스가 만료돼 다른 인스턴스가 재획득 가능하다(crash 복구).
    static final long LEASE_DURATION_SECONDS = 300L; // 5분

    private final TokenUsageOutboxRepository tokenUsageOutboxRepository;
    private final TokenUsageLedgerAppender ledgerAppender;
    // 백오프 시각·기록 귀속을 KST 기준으로 맞춘다(TokenMeteringService 와 같은 meetingClock).
    private final Clock clock;

    public TokenUsageRelay(TokenUsageOutboxRepository tokenUsageOutboxRepository,
                           TokenUsageLedgerAppender ledgerAppender,
                           @Qualifier("meetingClock") Clock clock) {
        this.tokenUsageOutboxRepository = tokenUsageOutboxRepository;
        this.ledgerAppender = ledgerAppender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${metering.token-usage-outbox.relay-interval-ms:60000}",
            initialDelayString = "${metering.token-usage-outbox.relay-initial-delay-ms:30000}")
    public void relay() {
        int processed = drainOnce();
        if (processed > 0) {
            log.info("토큰 사용량 outbox 릴레이 — {}건 원장 반영 완료.", processed);
        }
    }

    /**
     * 재시도할 때가 된 PENDING 항목을 한 배치 처리한다.
     *
     * <h2>동시성 보호</h2>
     * 각 항목을 처리하기 전에 원자적 클레임을 시도한다. 두 릴레이 인스턴스가 같은 항목을
     * 조회하더라도 클레임에 성공한 하나만 처리를 진행하고 나머지는 건너뛴다.
     *
     * @return 이번 실행에서 원장에 반영(DONE)된 건수. 테스트가 직접 호출한다.
     */
    public int drainOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TokenUsageOutbox> due = tokenUsageOutboxRepository.findDuePending(now, BATCH_SIZE);
        int delivered = 0;
        for (TokenUsageOutbox item : due) {
            LocalDateTime leaseExpiry = now.plusSeconds(LEASE_DURATION_SECONDS);
            if (!tokenUsageOutboxRepository.tryClaimForProcessing(item.getId(), leaseExpiry, now)) {
                // 다른 릴레이 인스턴스가 이미 클레임함 → 건너뜀.
                log.debug("outbox 클레임 실패(다른 인스턴스가 처리 중). jobId={}", item.getJobId());
                continue;
            }
            if (deliver(item)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean deliver(TokenUsageOutbox item) {
        LocalDateTime now = LocalDateTime.now(clock);
        // recorded_at 은 실패가 발생한 시점(=적재 시각)으로 되돌린다. "지금"으로 찍으면 월 경계를
        // 넘긴 재시도가 과금 기간을 잘못 귀속시킨다.
        TokenUsageRecord record = TokenUsageRecord.create(
                item.getCompanyId(), item.getTeamId(), item.getMeetingId(), item.getJobId(),
                item.getInputTokens(), item.getOutputTokens(), item.getModel(), item.getCreatedAt());
        try {
            ledgerAppender.append(record); // true=신규 반영, false=이미 존재 — 둘 다 성공(멱등)
            item.markDone(now);
            tokenUsageOutboxRepository.save(item);
            return true;
        } catch (RuntimeException e) {
            int nextAttempt = item.getAttemptCount() + 1;
            if (nextAttempt >= MAX_ATTEMPTS) {
                item.markDead(e.getMessage(), now);
                log.error("토큰 사용량 outbox dead-letter — {}회 시도 후 자동 복구 포기. jobId={}",
                        nextAttempt, item.getJobId(), e);
            } else {
                LocalDateTime next = now.plusSeconds(backoffSeconds(nextAttempt));
                item.markRetry(e.getMessage(), next, now);
                log.warn("토큰 사용량 outbox 재시도 예약(attempt={}, next={}). jobId={}",
                        nextAttempt, next, item.getJobId(), e);
            }
            tokenUsageOutboxRepository.save(item);
            return false;
        }
    }

    // 지수 백오프: 30s, 60s, 120s, 240s …
    private long backoffSeconds(int attempt) {
        return BASE_BACKOFF_SECONDS * (1L << (attempt - 1));
    }
}
