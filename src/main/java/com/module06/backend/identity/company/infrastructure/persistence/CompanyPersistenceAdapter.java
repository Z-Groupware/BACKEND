package com.module06.backend.identity.company.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRegistrationRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CompanyPersistenceAdapter implements CompanyRepository, CompanyRegistrationRepository {

    private final SpringDataCompanyRepository repository;
    private final EntityManager entityManager;

    @Override
    public Optional<Company> findByCode(String code) {
        return repository.findByCode(code)
                .map(e -> new Company(e.getId(), e.getCode(), e.getName()));
    }

    @Override
    public Optional<Company> findById(Long id) {
        return repository.findById(id)
                .map(e -> new Company(e.getId(), e.getCode(), e.getName()));
    }

    /**
     * {@code EntityManager.find} 로 직접 잠근다 — Spring Data 파생 메서드에 {@code @Lock} 을
     * 얹으려면 {@code findById} 를 오버라이드해야 하는데, 그러면 잠금이 필요 없는 다른 호출(예:
     * 발급 메일용 회사 코드 조회)까지 전부 잠기게 된다. 반드시 활성 트랜잭션 안에서 불러야 한다 —
     * 아니면 잠금이 요청과 함께 즉시 풀린다.
     */
    @Override
    @Transactional
    public void lockForUpdate(Long companyId) {
        CompanyJpaEntity company = entityManager.find(CompanyJpaEntity.class, companyId, LockModeType.PESSIMISTIC_WRITE);
        if (company == null) {
            throw new BusinessException(AuthErrorCode.COMPANY_CODE_NOT_FOUND);
        }
    }

    @Override
    public Long register(String code, String name, String registrationNo, String representativeName,
                         String managerEmail, String managerPhone, String employeeScale, String purpose,
                         boolean agreedMarketing, LocalDateTime now) {
        CompanyJpaEntity company = CompanyJpaEntity.register(
                code, name, registrationNo, representativeName,
                managerEmail, managerPhone, employeeScale, purpose, agreedMarketing, now);

        /*
         * saveAndFlush 다. 코드·사업자번호 UNIQUE 위반을 이 자리에서 터뜨려야 호출자가 잡아 코드를
         * 다시 뽑을 수 있다. save 만 하면 INSERT 가 커밋 시점까지 미뤄져, 재시도할 수 없는 곳에서
         * 예외가 난다.
         */
        return repository.saveAndFlush(company).getId();
    }

    @Override
    public boolean existsByRegistrationNo(String registrationNo) {
        return repository.existsByRegistrationNo(registrationNo);
    }
}
