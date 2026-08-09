package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDate;

/**
 * 회사 토큰 요금제 설정 요청. companyId 는 요청 본문이 아니라 인증 principal 에서만 채운다.
 * planCode·effectiveFrom 은 null 허용(서비스에서 기본값 적용), 수치는 음수·0풀 금지.
 */
public record SetCompanyTokenPlanCommand(
        String planCode,
        long monthlyTokenPool,
        int baseFee,
        int tokenOveragePricePer1k,
        LocalDate effectiveFrom
) {

    public SetCompanyTokenPlanCommand {
        if (monthlyTokenPool <= 0 || baseFee < 0 || tokenOveragePricePer1k < 0) {
            throw new BusinessException(MeteringErrorCode.MT_PLAN_COMMAND_INVALID);
        }
    }
}
