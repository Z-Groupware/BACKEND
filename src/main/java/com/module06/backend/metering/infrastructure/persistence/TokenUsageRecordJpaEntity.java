package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.TokenUsageRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_usage_record")
public class TokenUsageRecordJpaEntity {

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

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected TokenUsageRecordJpaEntity() {
    }

    private TokenUsageRecordJpaEntity(Long id, Long companyId, Long teamId, Long meetingId, String jobId,
                                      int inputTokens, int outputTokens, int totalTokens, String model,
                                      LocalDateTime recordedAt) {
        this.id = id;
        this.companyId = companyId;
        this.teamId = teamId;
        this.meetingId = meetingId;
        this.jobId = jobId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.model = model;
        this.recordedAt = recordedAt;
    }

    static TokenUsageRecordJpaEntity from(TokenUsageRecord record) {
        return new TokenUsageRecordJpaEntity(
                record.getId(),
                record.getCompanyId(),
                record.getTeamId(),
                record.getMeetingId(),
                record.getJobId(),
                record.getInputTokens(),
                record.getOutputTokens(),
                record.getTotalTokens(),
                record.getModel(),
                record.getRecordedAt()
        );
    }

    TokenUsageRecord toDomain() {
        return TokenUsageRecord.restore(id, companyId, teamId, meetingId, jobId, inputTokens, outputTokens,
                totalTokens, model, recordedAt);
    }
}
