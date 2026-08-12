package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.QuotaStatus;

import java.time.YearMonth;
import java.util.List;

public record MeteringDashboardResult(
        YearMonth period,
        long usedTokens,
        long monthlyTokenPool,
        long overageTokens,
        long estimatedAmountKrw,
        // 방향별(입력·출력) 단가로 계산한 실사용 금액. estimatedAmountKrw(총량 기준 예상 청구)와
        // 나란히 두어 "총량 단일 단가 vs 방향 차등"의 차이를 그대로 드러낸다.
        long directionalAmountKrw,
        QuotaStatus quotaStatus,
        List<DepartmentUsageResult> departments
) {
}
