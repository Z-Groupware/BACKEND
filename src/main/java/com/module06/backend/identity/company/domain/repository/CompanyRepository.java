package com.module06.backend.identity.company.domain.repository;

import java.util.Optional;

import com.module06.backend.identity.company.domain.model.Company;

public interface CompanyRepository {

    /** 정규화가 끝난 코드로 찾는다. 정규화는 호출자(서비스)가 한다. */
    Optional<Company> findByCode(String code);
}
