package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyTokenPlanCommand;
import com.module06.backend.metering.application.result.CompanyTokenPlanResult;
import com.module06.backend.metering.application.usecase.ManageCompanyTokenPlanUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class CompanyTokenPlanService implements ManageCompanyTokenPlanUseCase {

    private static final String OWNER_ROLE = "OWNER";
    private static final String DEFAULT_PLAN_CODE = "STANDARD";

    private final CompanyTokenPlanRepository companyTokenPlanRepository;
    // effectiveFrom 미지정 시 오늘 날짜를 KST 기준으로 잡는다. TokenMeteringService 와 같은 meetingClock 공유.
    private final Clock clock;

    public CompanyTokenPlanService(CompanyTokenPlanRepository companyTokenPlanRepository,
                                   @Qualifier("meetingClock") Clock clock) {
        this.companyTokenPlanRepository = companyTokenPlanRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CompanyTokenPlanResult setPlan(AuthPrincipal principal, SetCompanyTokenPlanCommand command) {
        Long companyId = requireOwnerOrAdmin(principal);

        String planCode = (command.planCode() == null || command.planCode().isBlank())
                ? DEFAULT_PLAN_CODE
                : command.planCode();
        LocalDate effectiveFrom = command.effectiveFrom() != null
                ? command.effectiveFrom()
                : LocalDate.now(clock);

        int inputPrice = command.resolvedInputPricePer1k();
        int outputPrice = command.resolvedOutputPricePer1k();

        // company_id 는 UNIQUE 다. 기존 요금제가 있으면 그 id 를 유지해 UPDATE, 없으면 새로 INSERT 한다.
        // (create 는 id=null 이라 그대로 저장하면 UNIQUE 위반이 난다.)
        CompanyTokenPlan plan = companyTokenPlanRepository.findByCompanyId(companyId)
                .map(existing -> CompanyTokenPlan.restore(existing.getId(), companyId, planCode,
                        command.monthlyTokenPool(), command.baseFee(), command.tokenOveragePricePer1k(),
                        inputPrice, outputPrice, effectiveFrom))
                .orElseGet(() -> CompanyTokenPlan.create(companyId, planCode,
                        command.monthlyTokenPool(), command.baseFee(), command.tokenOveragePricePer1k(),
                        inputPrice, outputPrice, effectiveFrom));

        return CompanyTokenPlanResult.from(companyTokenPlanRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyTokenPlanResult getPlan(AuthPrincipal principal) {
        Long companyId = requireOwnerOrAdmin(principal);
        return companyTokenPlanRepository.findByCompanyId(companyId)
                .map(CompanyTokenPlanResult::from)
                .orElseThrow(() -> new BusinessException(MeteringErrorCode.MT_PLAN_NOT_FOUND));
    }

    private Long requireOwnerOrAdmin(AuthPrincipal principal) {
        if (principal == null || principal.companyId() == null
                || (!principal.isAdmin() && !OWNER_ROLE.equals(principal.getAuthority()))) {
            throw new BusinessException(MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        }
        return principal.companyId();
    }
}
