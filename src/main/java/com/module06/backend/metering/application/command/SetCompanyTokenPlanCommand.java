package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDate;

/**
 * 회사 토큰 요금제 설정 요청. companyId 는 요청 본문이 아니라 인증 principal 에서만 채운다.
 * planCode·effectiveFrom 은 null 허용(서비스에서 기본값 적용), 수치는 음수·0풀 금지.
 *
 * inputTokenPricePer1k·outputTokenPricePer1k 는 null 허용 — 미지정 시 서비스가 총량 단가
 * (tokenOveragePricePer1k)로 채운다. 그래야 방향 단가를 명시하지 않은 기존 요청이 그대로 동작한다.
 */
public record SetCompanyTokenPlanCommand(
        String planCode,
        long monthlyTokenPool,
        int baseFee,
        int tokenOveragePricePer1k,
        Integer inputTokenPricePer1k,
        Integer outputTokenPricePer1k,
        LocalDate effectiveFrom
) {

    public SetCompanyTokenPlanCommand {
        if (monthlyTokenPool <= 0 || baseFee < 0 || tokenOveragePricePer1k < 0
                || (inputTokenPricePer1k != null && inputTokenPricePer1k < 0)
                || (outputTokenPricePer1k != null && outputTokenPricePer1k < 0)) {
            throw new BusinessException(MeteringErrorCode.MT_PLAN_COMMAND_INVALID);
        }
    }

    /** 방향 단가 미지정 시 총량 단가로 대체한 실효 입력 단가. */
    public int resolvedInputPricePer1k() {
        return inputTokenPricePer1k != null ? inputTokenPricePer1k : tokenOveragePricePer1k;
    }

    /** 방향 단가 미지정 시 총량 단가로 대체한 실효 출력 단가. */
    public int resolvedOutputPricePer1k() {
        return outputTokenPricePer1k != null ? outputTokenPricePer1k : tokenOveragePricePer1k;
    }
}
