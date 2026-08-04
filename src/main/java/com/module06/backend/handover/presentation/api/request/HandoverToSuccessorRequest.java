package com.module06.backend.handover.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/*
    #2 오너 일괄 이관+최종승인 요청.
    successorId = 전체 인계 액션을 넘겨받을 후임 1명.
    ownerId/ownerName = auth(B) 도입 전 임시로 받는 승인자(오너) 식별자·이름 스냅샷.
*/
public record HandoverToSuccessorRequest(
        @NotNull Long successorId,
        @NotNull Long ownerId,
        @NotBlank String ownerName
) {
}
