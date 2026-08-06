package com.module06.backend.identity.company.domain.repository;

import java.util.Optional;

import com.module06.backend.identity.company.domain.model.Company;

/**
 * 로그인 1단계가 쓰는 읽기 경로. 메서드를 하나로 유지한다 —
 * 쓰기는 {@link CompanyRegistrationRepository} 가 따로 맡는다.
 */
public interface CompanyRepository {

    Optional<Company> findByCode(String code);
}
