package com.module06.backend.identity.company.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRegistrationRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CompanyPersistenceAdapter implements CompanyRepository, CompanyRegistrationRepository {

    private final SpringDataCompanyRepository repository;

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
