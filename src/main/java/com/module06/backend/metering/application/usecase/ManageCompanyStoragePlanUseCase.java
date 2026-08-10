package com.module06.backend.metering.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyStoragePlanCommand;
import com.module06.backend.metering.application.result.CompanyStoragePlanResult;

/**
 * 회사 저장 용량 한도 설정·조회. 오너/어드민 전용(ManageCompanyTokenPlanUseCase와 동일 관문).
 * 한도가 있어야 CAP-01/02 presign 발급 시 용량 초과 판정이 동작하므로, 이 유스케이스가 스토리지
 * 미터링의 진입 설정이다.
 */
public interface ManageCompanyStoragePlanUseCase {

    /** 회사 저장 용량 한도를 upsert한다(company_id UNIQUE — 있으면 갱신, 없으면 생성). */
    CompanyStoragePlanResult setPlan(AuthPrincipal principal, SetCompanyStoragePlanCommand command);

    /** 현재 회사 저장 용량 한도를 조회한다. 미설정이면 MT_STORAGE_PLAN_NOT_FOUND. */
    CompanyStoragePlanResult getPlan(AuthPrincipal principal);
}
