package com.module06.backend.identity.company.domain.repository;

import java.util.Optional;

import com.module06.backend.identity.company.domain.model.Company;

public interface CompanyRepository {

    Optional<Company> findByCode(String code);
}
