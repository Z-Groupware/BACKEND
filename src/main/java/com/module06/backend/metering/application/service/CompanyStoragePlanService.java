package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyStoragePlanCommand;
import com.module06.backend.metering.application.result.CompanyStoragePlanResult;
import com.module06.backend.metering.application.usecase.ManageCompanyStoragePlanUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyStoragePlanService implements ManageCompanyStoragePlanUseCase {

    private static final String OWNER_ROLE = "OWNER";

    private final CompanyStoragePlanRepository companyStoragePlanRepository;

    public CompanyStoragePlanService(CompanyStoragePlanRepository companyStoragePlanRepository) {
        this.companyStoragePlanRepository = companyStoragePlanRepository;
    }

    @Override
    @Transactional
    public CompanyStoragePlanResult setPlan(AuthPrincipal principal, SetCompanyStoragePlanCommand command) {
        Long companyId = requireOwnerOrAdmin(principal);

        // company_id는 UNIQUE다. 기존 한도가 있으면 그 id를 유지해 UPDATE, 없으면 새로 INSERT한다
        // (CompanyTokenPlanService.setPlan과 동일 패턴 — create는 id=null이라 그대로 저장하면
        // UNIQUE 위반이 난다).
        CompanyStoragePlan plan = companyStoragePlanRepository.findByCompanyId(companyId)
                .map(existing -> CompanyStoragePlan.restore(existing.getId(), companyId, command.storageCapBytes()))
                .orElseGet(() -> CompanyStoragePlan.create(companyId, command.storageCapBytes()));

        return CompanyStoragePlanResult.from(companyStoragePlanRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyStoragePlanResult getPlan(AuthPrincipal principal) {
        Long companyId = requireOwnerOrAdmin(principal);
        return companyStoragePlanRepository.findByCompanyId(companyId)
                .map(CompanyStoragePlanResult::from)
                .orElseThrow(() -> new BusinessException(MeteringErrorCode.MT_STORAGE_PLAN_NOT_FOUND));
    }

    private Long requireOwnerOrAdmin(AuthPrincipal principal) {
        if (principal == null || principal.companyId() == null
                || (!principal.isAdmin() && !OWNER_ROLE.equals(principal.getAuthority()))) {
            throw new BusinessException(MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        }
        return principal.companyId();
    }
}
