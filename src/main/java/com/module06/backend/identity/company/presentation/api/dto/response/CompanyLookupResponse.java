package com.module06.backend.identity.company.presentation.api.dto.response;

import com.module06.backend.identity.company.domain.model.Company;


public record CompanyLookupResponse(String code, String name) {

    public static CompanyLookupResponse from(Company company) {
        return new CompanyLookupResponse(company.code(), company.name());
    }
}
