package com.module06.backend.identity.company.application.usecase;

import com.module06.backend.identity.company.application.command.RegisterCompanyCommand;
import com.module06.backend.identity.company.application.dto.CompanyRegistrationResult;

/**
 * 기업 등록 신청(API 27). 운영자 승인 절차가 없어 <b>신청이 곧 생성</b>이다 —
 * 회사·오너 계정이 만들어지고 메일이 나간다.
 */
public interface RegisterCompanyUseCase {

    CompanyRegistrationResult register(RegisterCompanyCommand command);
}
