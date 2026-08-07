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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class TokenMeteringService implements RecordTokenUsageUseCase, TokenQuotaPort {

    private final TokenUsageRecordRepository tokenUsageRecordRepository;
    private final CompanyTokenPlanRepository companyTokenPlanRepository;

    public TokenMeteringService(TokenUsageRecordRepository tokenUsageRecordRepository,
                                CompanyTokenPlanRepository companyTokenPlanRepository) {
        this.tokenUsageRecordRepository = tokenUsageRecordRepository;
        this.companyTokenPlanRepository = companyTokenPlanRepository;
    }

    @Override
    @Transactional
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
                LocalDateTime.now()
        );

        try {
            tokenUsageRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
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
        YearMonth period = YearMonth.now();
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
