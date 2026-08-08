package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDateTime;
import java.util.Objects;

public class TokenUsageRecord {

    private final Long id;
    private final Long companyId;
    private final Long teamId;
    private final Long meetingId;
    private final String jobId;
    private final int inputTokens;
    private final int outputTokens;
    private final int totalTokens;
    private final String model;
    private final LocalDateTime recordedAt;

    private TokenUsageRecord(Long id, Long companyId, Long teamId, Long meetingId, String jobId,
                             int inputTokens, int outputTokens, int totalTokens, String model,
                             LocalDateTime recordedAt) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.teamId = teamId;
        this.meetingId = Objects.requireNonNull(meetingId, "meetingId must not be null");
        this.jobId = requireText(jobId);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.model = requireText(model);
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    public static TokenUsageRecord create(Long companyId, Long teamId, Long meetingId, String jobId,
                                          int inputTokens, int outputTokens, String model,
                                          LocalDateTime recordedAt) {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new BusinessException(MeteringErrorCode.MT_RECORD_COMMAND_INVALID);
        }
        return new TokenUsageRecord(null, companyId, teamId, meetingId, jobId, inputTokens, outputTokens,
                inputTokens + outputTokens, model, recordedAt);
    }

    public static TokenUsageRecord restore(Long id, Long companyId, Long teamId, Long meetingId, String jobId,
                                           int inputTokens, int outputTokens, int totalTokens, String model,
                                           LocalDateTime recordedAt) {
        return new TokenUsageRecord(id, companyId, teamId, meetingId, jobId, inputTokens, outputTokens,
                totalTokens, model, recordedAt);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(MeteringErrorCode.MT_RECORD_COMMAND_INVALID);
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public String getJobId() {
        return jobId;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public String getModel() {
        return model;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
