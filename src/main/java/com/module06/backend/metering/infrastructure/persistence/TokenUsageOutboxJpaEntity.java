package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.OutboxStatus;
import com.module06.backend.metering.domain.model.TokenUsageOutbox;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_usage_outbox")
public class TokenUsageOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "job_id", nullable = false, length = 100, unique = true)
    private String jobId;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    /** 릴레이 리스 만료 시각. null = 미클레임(처리 가능). 만료되면 다른 인스턴스가 재획득 가능. */
    @Column(name = "claimed_until")
    private LocalDateTime claimedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TokenUsageOutboxJpaEntity() {
    }

    private TokenUsageOutboxJpaEntity(Long id, Long companyId, Long teamId, Long meetingId, String jobId,
                                      int inputTokens, int outputTokens, String model, OutboxStatus status,
                                      int attemptCount, String lastError, LocalDateTime nextAttemptAt,
                                      LocalDateTime claimedUntil, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.companyId = companyId;
        this.teamId = teamId;
        this.meetingId = meetingId;
        this.jobId = jobId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.model = model;
        this.status = status;
        this.attemptCount = attemptCount;
        this.lastError = lastError;
        this.nextAttemptAt = nextAttemptAt;
        this.claimedUntil = claimedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static TokenUsageOutboxJpaEntity from(TokenUsageOutbox outbox) {
        return new TokenUsageOutboxJpaEntity(
                outbox.getId(),
                outbox.getCompanyId(),
                outbox.getTeamId(),
                outbox.getMeetingId(),
                outbox.getJobId(),
                outbox.getInputTokens(),
                outbox.getOutputTokens(),
                outbox.getModel(),
                outbox.getStatus(),
                outbox.getAttemptCount(),
                outbox.getLastError(),
                outbox.getNextAttemptAt(),
                null,   // claimed_until 은 DB 의 conditional UPDATE 로만 변경한다. 도메인 저장 시엔 건드리지 않는다.
                outbox.getCreatedAt(),
                outbox.getUpdatedAt()
        );
    }

    TokenUsageOutbox toDomain() {
        return TokenUsageOutbox.restore(id, companyId, teamId, meetingId, jobId, inputTokens, outputTokens, model,
                status, attemptCount, lastError, nextAttemptAt, createdAt, updatedAt);
    }
}
