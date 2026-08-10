package com.module06.backend.metering.presentation.api.dto.request;

import com.module06.backend.metering.application.command.SetCompanyStoragePlanCommand;

/**
 * 회사 저장 용량 한도 설정 요청 본문. companyId는 담지 않는다 — 인증 principal에서만 채운다.
 */
public record SetCompanyStoragePlanRequest(long storageCapBytes) {

    public SetCompanyStoragePlanCommand toCommand() {
        return new SetCompanyStoragePlanCommand(storageCapBytes);
    }
}
