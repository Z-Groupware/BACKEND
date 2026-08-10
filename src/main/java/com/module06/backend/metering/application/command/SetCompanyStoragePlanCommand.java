package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

/**
 * 회사 저장 용량 한도 설정 요청. companyId는 요청 본문이 아니라 인증 principal에서만 채운다
 * (SetCompanyTokenPlanCommand와 동일 원칙).
 */
public record SetCompanyStoragePlanCommand(long storageCapBytes) {

    public SetCompanyStoragePlanCommand {
        if (storageCapBytes <= 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_PLAN_COMMAND_INVALID);
        }
    }
}
