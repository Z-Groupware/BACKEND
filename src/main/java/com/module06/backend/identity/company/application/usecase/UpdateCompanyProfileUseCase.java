package com.module06.backend.identity.company.application.usecase;

import com.module06.backend.identity.company.application.command.UpdateCompanyCommand;
import com.module06.backend.identity.company.domain.model.Company;

/** §4-3. 부분 수정 — {@code code}는 로그인 키라 대상이 아니다. */
public interface UpdateCompanyProfileUseCase {

    Company updateProfile(UpdateCompanyCommand command);
}
