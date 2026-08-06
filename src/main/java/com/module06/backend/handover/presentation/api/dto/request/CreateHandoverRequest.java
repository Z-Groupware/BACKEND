package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.domain.model.HandoverType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 신청자(writerMemberId)·소속 팀(teamId)은 본문에서 받지 않는다 — 받으면 남의 사번을 적어
 * 그 사람 명의로 인수인계를 대신 신청할 수 있다. 토큰(JWT principal)에서만 꺼내 toCommand로 주입한다.
 */
public record CreateHandoverRequest(
        @NotNull HandoverType handoverType,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        List<Long> selectedActionIds
) {

    public CreateHandoverCommand toCommand(Long writerMemberId, Long teamId) {
        return new CreateHandoverCommand(
                writerMemberId,
                teamId,
                handoverType,
                leaveStartAt,
                leaveEndAt,
                lastWorkingDay,
                selectedActionIds
        );
    }
}
