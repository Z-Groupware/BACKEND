package com.module06.backend.action.presentation.api.request;

import java.util.List;

import com.module06.backend.action.domain.model.ActionStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/* comment.
    개인·팀 액션 보드 "저장" 버튼의 일괄 상태 변경 요청 DTO(FR-AC-03, FR-AC-07 공용).
    형태: { "items": [ { "actionId": 1, "status": "DONE" }, ... ] }
    items는 비어있을 수 없고, 한 항목이라도 실패하면 전체가 실패한다(all-or-nothing).

    연결된 클래스
    - ActionController · TeamActionController                                    : 이 DTO를 받는 진입점
    - BulkUpdateActionStatusCommand · BulkUpdateTeamActionStatusCommand           : 이 DTO가 변환되는 application 명령
*/
public record BulkUpdateActionStatusRequest(
        @NotEmpty List<@NotNull @Valid Item> items
) {

    public record Item(
            @NotNull Long actionId,
            @NotNull ActionStatus status
    ) {
    }
}
