package com.module06.backend.metering.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyTokenPlanCommand;
import com.module06.backend.metering.application.result.CompanyTokenPlanResult;

/**
 * 회사 토큰 요금제 설정·조회. 오너/어드민 전용(대시보드 회사 스코프와 동일 관문).
 * 요금제가 있어야 대시보드·쿼터가 동작하므로, 이 유스케이스가 미터링 기능의 진입 설정이다.
 */
public interface ManageCompanyTokenPlanUseCase {

    /** 회사 요금제를 upsert 한다(company_id UNIQUE — 있으면 갱신, 없으면 생성). */
    CompanyTokenPlanResult setPlan(AuthPrincipal principal, SetCompanyTokenPlanCommand command);

    /** 현재 회사 요금제를 조회한다. 미설정이면 MT_PLAN_NOT_FOUND. */
    CompanyTokenPlanResult getPlan(AuthPrincipal principal);
}
