package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.RecordTokenUsageCommand;
import com.module06.backend.metering.application.result.QuotaStatusResult;
import com.module06.backend.metering.application.usecase.RecordTokenUsageUseCase;
import com.module06.backend.metering.application.port.in.TokenQuotaPort;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class TokenMeteringService implements RecordTokenUsageUseCase, TokenQuotaPort {

    private final TokenUsageRecordRepository tokenUsageRecordRepository;
    private final CompanyTokenPlanRepository companyTokenPlanRepository;
    // 청구·과금 귀속은 KST 기준이어야 한다. meeting 모듈이 이미 KST Clock(meetingClock)을 노출하므로
    // 새 Clock 빈을 만들면 by-type 주입이 모호해진다 — 같은 KST 빈을 재사용해 모듈 간 시각을 일치시킨다.
    private final Clock clock;

    public TokenMeteringService(TokenUsageRecordRepository tokenUsageRecordRepository,
                                CompanyTokenPlanRepository companyTokenPlanRepository,
                                @Qualifier("meetingClock") Clock clock) {
        this.tokenUsageRecordRepository = tokenUsageRecordRepository;
        this.companyTokenPlanRepository = companyTokenPlanRepository;
        this.clock = clock;
    }

    /*
     * @Transactional 을 두지 않는다. record 는 단일 INSERT 뿐이고, 멱등 보장은 job_id UNIQUE 제약이 한다.
     *
     * 만약 @Transactional 이면, 동시 중복(같은 job_id 2요청)에서 save 의 제약 위반이 트랜잭션을
     * rollback-only 로 마킹해, 예외를 잡아도 커밋 시점에 UnexpectedRollbackException 이 터진다.
     * 트랜잭션 없이 두면 save 는 자기 트랜잭션에서 돌고, 위반 시 그 트랜잭션만 롤백하며 예외를
     * 여기로 던지므로 깔끔히 no-op 으로 삼킬 수 있다(바깥 트랜잭션 오염 없음).
     */
    @Override
    public void record(RecordTokenUsageCommand command) {
        if (tokenUsageRecordRepository.existsByJobId(command.jobId())) {
            return;
        }

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
            tokenUsageRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복이 job_id UNIQUE 에 걸린 것 — 이미 기록됐다는 뜻이라 멱등 no-op.
            return;
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
