package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.handover.application.command.ReassignItemsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일괄 재분배 요청 — 휴직 신청 화면(팀장 자가 재할당)이 액션별 담당자를 한 번에 보낸다.
 * 건별 {@link ReassignItemRequest}(경로에 actionId)와 달리 본문에 (actionId, toMemberId) 목록을 담는다.
 */
public record ReassignItemsRequest(
        @NotEmpty @Valid List<Assignment> assignments
) {

    public record Assignment(
            @NotNull Long actionId,
            @NotNull Long toMemberId
    ) {
    }

    public ReassignItemsCommand toCommand(Long handoverId, LocalDateTime reassignedAt) {
        return new ReassignItemsCommand(
                handoverId,
                assignments.stream()
                        .map(a -> new ReassignItemsCommand.Assignment(a.actionId(), a.toMemberId()))
                        .toList(),
                reassignedAt
        );
    }
}
