package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface GetHandoverListUseCase {

    List<HandoverSummary> list(HandoverListQuery query);

    record HandoverListQuery(
            Long companyId,
            Long writerMemberId,
            Long teamId,
            HandoverStatus status
    ) {
    }

    record HandoverSummary(
            Long id,
            Long writerMemberId,
            String writerName,
            String writerPosition,
            Long teamId,
            HandoverType handoverType,
            HandoverStatus status,
            LocalDateTime leaveStartAt,
            LocalDateTime leaveEndAt,
            LocalDate lastWorkingDay,
            int itemCount,
            int reassignRequiredCount,
            int reassignedCount
    ) {
    }
}
