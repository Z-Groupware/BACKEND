package com.module06.backend.identity.company.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CompanyPersistenceAdapter implements CompanyRepository {

    private final SpringDataCompanyRepository repository;

    @Override
    public Optional<Company> findByCode(String code) {
        return repository.findByCode(code)
                .map(e -> new Company(e.getId(), e.getCode(), e.getName()));
    }
}
