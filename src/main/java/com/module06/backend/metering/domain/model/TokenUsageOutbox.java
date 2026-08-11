package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 원장 기록에 실패한 토큰 사용량을 담아 두는 durable outbox 항목.
 *
 * 원장(token_usage_record)이 job_id 로 멱등하기 때문에, 이 항목을 몇 번 재적용해도
 * 원장은 한 줄만 남는다 — 그래서 at-least-once 재시도가 안전하다. 상태 전이는
 * PENDING →(성공) DONE / (재시도) PENDING / (한계 초과) DEAD 뿐이다.
 */
public class TokenUsageOutbox {

    private final Long id;
    private final Long companyId;
    private final Long teamId;
    private final Long meetingId;
    private final String jobId;
    private final int inputTokens;
    private final int outputTokens;
    private final String model;

    private OutboxStatus status;
    private int attemptCount;
    private String lastError;
    private LocalDateTime nextAttemptAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private TokenUsageOutbox(Long id, Long companyId, Long teamId, Long meetingId, String jobId,
                             int inputTokens, int outputTokens, String model, OutboxStatus status,
                             int attemptCount, String lastError, LocalDateTime nextAttemptAt,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.teamId = teamId;
        this.meetingId = Objects.requireNonNull(meetingId, "meetingId must not be null");
        this.jobId = requireText(jobId);
        if (inputTokens < 0 || outputTokens < 0) {
            throw new BusinessException(MeteringErrorCode.MT_RECORD_COMMAND_INVALID);
        }
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.model = requireText(model);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.attemptCount = attemptCount;
        this.lastError = lastError;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** 실패한 기록을 즉시 재시도 대상(PENDING, nextAttemptAt=now)으로 적재한다. */
    public static TokenUsageOutbox enqueue(Long companyId, Long teamId, Long meetingId, String jobId,
                                           int inputTokens, int outputTokens, String model, LocalDateTime now) {
        return new TokenUsageOutbox(null, companyId, teamId, meetingId, jobId, inputTokens, outputTokens, model,
                OutboxStatus.PENDING, 0, null, now, now, now);
    }

    public static TokenUsageOutbox restore(Long id, Long companyId, Long teamId, Long meetingId, String jobId,
                                           int inputTokens, int outputTokens, String model, OutboxStatus status,
                                           int attemptCount, String lastError, LocalDateTime nextAttemptAt,
                                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new TokenUsageOutbox(id, companyId, teamId, meetingId, jobId, inputTokens, outputTokens, model,
                status, attemptCount, lastError, nextAttemptAt, createdAt, updatedAt);
    }

    /** 원장 반영 성공. 더 이상 처리하지 않는다. */
    public void markDone(LocalDateTime now) {
        this.status = OutboxStatus.DONE;
        this.updatedAt = now;
    }

    /** 재시도로 남긴다 — 시도 횟수를 올리고 다음 시도 시각을 뒤로 민다. */
    public void markRetry(String error, LocalDateTime nextAttemptAt, LocalDateTime now) {
        this.attemptCount += 1;
        this.status = OutboxStatus.PENDING;
        this.lastError = clip(error);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }

    /** 한계 시도 초과 — 자동 복구를 포기하고 dead-letter 로 표시한다. */
    public void markDead(String error, LocalDateTime now) {
        this.attemptCount += 1;
        this.status = OutboxStatus.DEAD;
        this.lastError = clip(error);
        this.updatedAt = now;
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

    public String getModel() {
        return model;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(MeteringErrorCode.MT_RECORD_COMMAND_INVALID);
        }
        return value;
    }

    // last_error 컬럼은 VARCHAR(500) 이다. 넘치는 메시지로 저장이 터지면 실패 기록 자체가 사라져
    // "왜 실패했는지 모르는 실패"가 된다 — 조사 출발점을 지키려고 자른다.
    private static String clip(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
