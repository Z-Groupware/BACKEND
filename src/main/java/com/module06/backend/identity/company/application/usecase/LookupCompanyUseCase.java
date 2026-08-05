package com.module06.backend.identity.company.application.usecase;

import com.module06.backend.identity.company.domain.model.Company;

public interface LookupCompanyUseCase {

    Company lookup(String rawCode);
}
