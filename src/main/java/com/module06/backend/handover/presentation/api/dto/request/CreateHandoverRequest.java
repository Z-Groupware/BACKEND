package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.domain.model.HandoverType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateHandoverRequest(
        @NotNull HandoverType handoverType,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        List<Long> selectedActionIds
) {

    // 작성자(writerMemberId)·팀(teamId)은 본문으로 받지 않는다. 받으면 로그인만 하면 남의
    // 사번을 적어 그 사람 이름으로 인계서를 만들 수 있다(#101·#102 에서 걷어낸 그 구멍).
    // 인계서는 본인이 본인 것을 내는 흐름이라 둘 다 토큰에서 꺼내 컨트롤러가 넘긴다.
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
