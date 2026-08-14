package com.module06.backend.metering.application.usecase;

import com.module06.backend.metering.application.result.StorageOverviewResult;

public interface GetStorageOverviewUseCase {

    /** companyId는 JWT에서만 받는다(요청 파라미터로 받지 않음) — 다른 회사 저장소를 들여다볼 수 없게. */
    StorageOverviewResult getOverview(Long companyId);
}
