package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyStoragePlanCommand;
import com.module06.backend.metering.application.result.CompanyStoragePlanResult;
import com.module06.backend.metering.application.usecase.ManageCompanyStoragePlanUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyStoragePlanService implements ManageCompanyStoragePlanUseCase {

    private static final String OWNER_ROLE = "OWNER";

    private final CompanyStoragePlanRepository companyStoragePlanRepository;

    public CompanyStoragePlanService(CompanyStoragePlanRepository companyStoragePlanRepository) {
        this.companyStoragePlanRepository = companyStoragePlanRepository;
    }

    /*
     * @Transactional을 메서드 전체에 두지 않는다 — TokenMeteringService.record와 동일한 이유다.
     * saveAndFlush 실패(DataIntegrityViolationException)를 여기서 잡아도, 그 실패가 같은
     * @Transactional 경계 안에서 일어난 것이면 Hibernate 세션이 이미 rollback-only로 마킹돼
     * 커밋 시점에 UnexpectedRollbackException이 터진다. 트랜잭션 경계를 메서드에 두지 않으면
     * findByCompanyId·save 각각이 스프링 데이터 리포지토리 자신의 트랜잭션으로 독립적으로 돌아
     * 실패한 시도가 다음 재시도를 오염시키지 않는다.
     */
    @Override
    public CompanyStoragePlanResult setPlan(AuthPrincipal principal, SetCompanyStoragePlanCommand command) {
        Long companyId = requireOwnerOrAdmin(principal);

        // company_id는 UNIQUE다. 기존 한도가 있으면 그 id를 유지해 UPDATE, 없으면 새로 INSERT한다
        // (CompanyTokenPlanService.setPlan과 동일 패턴 — create는 id=null이라 그대로 저장하면
        // UNIQUE 위반이 난다).
        CompanyStoragePlan plan = companyStoragePlanRepository.findByCompanyId(companyId)
                .map(existing -> CompanyStoragePlan.restore(existing.getId(), companyId, command.storageCapBytes()))
                .orElseGet(() -> CompanyStoragePlan.create(companyId, command.storageCapBytes()));

        try {
            return CompanyStoragePlanResult.from(companyStoragePlanRepository.save(plan));
        } catch (DataIntegrityViolationException e) {
            // 두 요청이 동시에 최초 설정을 시도한 경합(TOCTOU) — 이긴 쪽이 이미 만든 행을 다시
            // 조회해 UPDATE로 재시도한다(recording 테이블의 "DB 제약이 최종 방어선" 패턴과 동일).
            CompanyStoragePlan existing = companyStoragePlanRepository.findByCompanyId(companyId)
                    .orElseThrow(() -> e);
            CompanyStoragePlan updated = CompanyStoragePlan.restore(existing.getId(), companyId,
                    command.storageCapBytes());
            return CompanyStoragePlanResult.from(companyStoragePlanRepository.save(updated));
        }
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
