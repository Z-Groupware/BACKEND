package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;
import com.module06.backend.metering.application.port.in.StorageQuotaPort;
import com.module06.backend.metering.application.result.StorageQuotaStatusResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class StorageMeteringService implements ReportMeetingStorageUsagePort, StorageQuotaPort {

    private final MeetingStorageUsageRepository meetingStorageUsageRepository;
    private final CompanyStoragePlanRepository companyStoragePlanRepository;
    // 다른 미터링 서비스와 동일한 KST 기준 Clock을 재사용한다(TokenMeteringService 주석 참고 —
    // 모듈 간 시각을 일치시키려고 새 Clock 빈을 따로 만들지 않는다).
    private final Clock clock;

    public StorageMeteringService(MeetingStorageUsageRepository meetingStorageUsageRepository,
                                  CompanyStoragePlanRepository companyStoragePlanRepository,
                                  @Qualifier("meetingClock") Clock clock) {
        this.meetingStorageUsageRepository = meetingStorageUsageRepository;
        this.companyStoragePlanRepository = companyStoragePlanRepository;
        this.clock = clock;
    }

    /*
     * find-or-create가 필요 없다 — CompanyStoragePlan과 달리 meetingId 자체가 식별자라 보존할
     * 별도 auto-increment id가 없다. save()가 있으면 갱신·없으면 삽입을 알아서 처리한다(JPA가
     * 미배정 아닌 식별자로 판단).
     *
     * @Transactional을 두지 않는다 — TokenMeteringService.record와 같은 이유다. 단일 save 뿐이고,
     * meetingId PK가 최종 방어선이다.
     */
    @Override
    public void report(ReportMeetingStorageUsageCommand command) {
        MeetingStorageUsage usage = MeetingStorageUsage.report(command.meetingId(), command.companyId(),
                command.usedBytes(), LocalDateTime.now(clock));
        meetingStorageUsageRepository.save(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public StorageQuotaStatusResult getStatus(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        }
        CompanyStoragePlan plan = companyStoragePlanRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessException(MeteringErrorCode.MT_STORAGE_PLAN_NOT_FOUND));
        long usedBytes = meetingStorageUsageRepository.sumUsedBytesByCompanyId(companyId);
        return new StorageQuotaStatusResult(companyId, usedBytes, plan.getStorageCapBytes(),
                plan.quotaStatus(usedBytes));
    }
}
