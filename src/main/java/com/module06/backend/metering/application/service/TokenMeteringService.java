package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.RecordTokenUsageCommand;
import com.module06.backend.metering.application.result.QuotaStatusResult;
import com.module06.backend.metering.application.usecase.RecordTokenUsageUseCase;
import com.module06.backend.metering.application.port.in.TokenQuotaPort;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.model.TokenUsageOutbox;
import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import com.module06.backend.metering.domain.repository.TokenUsageOutboxRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class TokenMeteringService implements RecordTokenUsageUseCase, TokenQuotaPort {

    private static final Logger log = LoggerFactory.getLogger(TokenMeteringService.class);

    private final TokenUsageRecordRepository tokenUsageRecordRepository;
    private final TokenUsageOutboxRepository tokenUsageOutboxRepository;
    private final TokenUsageLedgerAppender ledgerAppender;
    private final CompanyTokenPlanRepository companyTokenPlanRepository;
    // 청구·과금 귀속은 KST 기준이어야 한다. meeting 모듈이 이미 KST Clock(meetingClock)을 노출하므로
    // 새 Clock 빈을 만들면 by-type 주입이 모호해진다 — 같은 KST 빈을 재사용해 모듈 간 시각을 일치시킨다.
    private final Clock clock;

    public TokenMeteringService(TokenUsageRecordRepository tokenUsageRecordRepository,
                                TokenUsageOutboxRepository tokenUsageOutboxRepository,
                                TokenUsageLedgerAppender ledgerAppender,
                                CompanyTokenPlanRepository companyTokenPlanRepository,
                                @Qualifier("meetingClock") Clock clock) {
        this.tokenUsageRecordRepository = tokenUsageRecordRepository;
        this.tokenUsageOutboxRepository = tokenUsageOutboxRepository;
        this.ledgerAppender = ledgerAppender;
        this.companyTokenPlanRepository = companyTokenPlanRepository;
        this.clock = clock;
    }

    /*
     * 정상 경로는 원장에 바로 기록한다(동기·멱등). 이 호출은 @Transactional 이 아니다 —
     * 멱등 규칙과 그 이유는 TokenUsageLedgerAppender 에 모여 있다.
     *
     * <h2>실패해도 잃지 않는다</h2>
     * 원장 반영이 실패하면(DB 순단·데드락·타임아웃, 또는 job_id 중복이 아닌 무결성 위반) 예외를
     * 삼키고 끝내지 않고 durable outbox 에 적재한다. 릴레이가 백오프로 재시도하고, 한계 초과분은
     * dead-letter 로 남긴다. 멱등 원장(중복 차단) + at-least-once 릴레이(재시도) = 효과적 exactly-once.
     */
    @Override
    public void record(RecordTokenUsageCommand command) {
        TokenUsageRecord record = TokenUsageRecord.create(
                command.companyId(),
                command.teamId(),
                command.meetingId(),
                command.jobId(),
                command.inputTokens(),
                command.outputTokens(),
                command.model(),
                LocalDateTime.now(clock)
        );

        try {
            ledgerAppender.append(record);
        } catch (RuntimeException e) {
            // 원장 반영 실패 → 유실 대신 durable 재시도로 넘긴다.
            enqueueForRetry(command, e);
        }
    }

    /*
     * 실패한 기록을 outbox 에 적재한다. outbox 도 job_id UNIQUE 라 중복 적재되지 않는다.
     * outbox 적재까지 실패하면 그제야 유실이므로 크게 로깅한다(잔여 위험, 관측 가능).
     */
    private void enqueueForRetry(RecordTokenUsageCommand command, RuntimeException cause) {
        if (tokenUsageOutboxRepository.existsByJobId(command.jobId())) {
            return;
        }
        TokenUsageOutbox outbox = TokenUsageOutbox.enqueue(
                command.companyId(), command.teamId(), command.meetingId(), command.jobId(),
                command.inputTokens(), command.outputTokens(), command.model(), LocalDateTime.now(clock));
        try {
            tokenUsageOutboxRepository.save(outbox);
            log.warn("토큰 원장 반영 실패 — outbox 적재해 재시도로 넘긴다. jobId={}", command.jobId(), cause);
        } catch (DataIntegrityViolationException dup) {
            // outbox job_id 동시 중복이면 이미 적재됨 → no-op.
            if (tokenUsageOutboxRepository.existsByJobId(command.jobId())) {
                return;
            }
            log.error("토큰 원장·outbox 모두 반영 실패 — 사용량 한 건이 유실될 수 있다. jobId={}", command.jobId(), dup);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaStatusResult getStatus(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        }
        CompanyTokenPlan plan = companyTokenPlanRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessException(MeteringErrorCode.MT_PLAN_NOT_FOUND));
        YearMonth period = YearMonth.now(clock);
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();
        long usedTokens = tokenUsageRecordRepository.sumTotalTokens(companyId, start, end);
        return new QuotaStatusResult(
                companyId,
                period,
                usedTokens,
                plan.getMonthlyTokenPool(),
                plan.overageTokens(usedTokens),
                plan.quotaStatus(usedTokens)
        );
    }
}
