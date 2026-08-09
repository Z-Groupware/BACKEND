package com.module06.backend.identity.company.application.usecase;

import com.module06.backend.identity.company.domain.model.Company;

/** §4-2. 기업 설정 > 기본 정보 탭. */
public interface GetCompanyProfileUseCase {

    Company getProfile(Long companyId);
}
