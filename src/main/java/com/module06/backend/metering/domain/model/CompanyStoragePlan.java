package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.util.Objects;

/**
 * 회사별 저장 용량(스토리지) 한도. CompanyTokenPlan과 달리 월별 리셋·초과 과금이 없다 — 스토리지는
 * 늘었다 줄었다 하는 게이지(청크 업로드로 늘고, 조립·삭제로 준다)라서 "이번 달 사용량"이라는
 * 개념 자체가 없고, 그냥 "지금 총 사용량이 한도를 넘었는가"만 본다.
 */
public class CompanyStoragePlan {

    private final Long id;
    private final Long companyId;
    private final long storageCapBytes;

    private CompanyStoragePlan(Long id, Long companyId, long storageCapBytes) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        if (storageCapBytes <= 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_PLAN_COMMAND_INVALID);
        }
        this.id = id;
        this.storageCapBytes = storageCapBytes;
    }

    public static CompanyStoragePlan create(Long companyId, long storageCapBytes) {
        return new CompanyStoragePlan(null, companyId, storageCapBytes);
    }

    public static CompanyStoragePlan restore(Long id, Long companyId, long storageCapBytes) {
        return new CompanyStoragePlan(id, companyId, storageCapBytes);
    }

    // 80% 미만: WITHIN, 80%~100% 미만: SOFT_WARN, 100% 이상: OVER. CompanyTokenPlan.quotaStatus와
    // 동일한 경계값 — 미터링 전반에서 "80%가 경고선"이라는 규칙을 통일한다.
    public QuotaStatus quotaStatus(long usedBytes) {
        if (usedBytes < storageCapBytes * 0.8d) {
            return QuotaStatus.WITHIN;
        }
        if (usedBytes < storageCapBytes) {
            return QuotaStatus.SOFT_WARN;
        }
        return QuotaStatus.OVER;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public long getStorageCapBytes() {
        return storageCapBytes;
    }
}
