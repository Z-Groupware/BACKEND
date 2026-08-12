package com.module06.backend.metering.presentation.api.dto.request;

import com.module06.backend.metering.application.command.SetCompanyTokenPlanCommand;

import java.time.LocalDate;

/**
 * 회사 토큰 요금제 설정 요청 본문. companyId 는 담지 않는다 — 인증 principal 에서만 채운다.
 * planCode·effectiveFrom 은 선택(미지정 시 서버가 STANDARD·오늘로 채움).
 * inputTokenPricePer1k·outputTokenPricePer1k 도 선택(미지정 시 총량 단가로 채움).
 */
public record SetCompanyTokenPlanRequest(
        String planCode,
        long monthlyTokenPool,
        int baseFee,
        int tokenOveragePricePer1k,
        Integer inputTokenPricePer1k,
        Integer outputTokenPricePer1k,
        LocalDate effectiveFrom
) {

    public SetCompanyTokenPlanCommand toCommand() {
        return new SetCompanyTokenPlanCommand(
                planCode, monthlyTokenPool, baseFee, tokenOveragePricePer1k,
                inputTokenPricePer1k, outputTokenPricePer1k, effectiveFrom);
    }
}
