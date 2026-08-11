package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CompanyStoragePlanPersistenceAdapter implements CompanyStoragePlanRepository {

    private final SpringDataCompanyStoragePlanRepository repository;

    public CompanyStoragePlanPersistenceAdapter(SpringDataCompanyStoragePlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CompanyStoragePlan> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(CompanyStoragePlanJpaEntity::toDomain);
    }

    // IDENTITY 채번은 insert 자체가 즉시 나가지만, saveAndFlush로 명시해 UNIQUE(company_id) 위반이
    // 이 호출 안에서 곧바로 터지게 한다(TokenUsageRecordPersistenceAdapter.save와 동일 관례) —
    // 호출자(CompanyStoragePlanService)가 DataIntegrityViolationException을 여기서 바로 잡을 수 있어야
    // 동시 최초 설정 레이스를 재시도로 복구할 수 있다.
    @Override
    public CompanyStoragePlan save(CompanyStoragePlan plan) {
        return repository.saveAndFlush(CompanyStoragePlanJpaEntity.from(plan)).toDomain();
    }
}
