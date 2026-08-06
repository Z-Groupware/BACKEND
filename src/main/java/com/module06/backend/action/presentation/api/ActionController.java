package com.module06.backend.action.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;

import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.presentation.api.request.CreateActionRequest;
import com.module06.backend.action.presentation.api.response.ActionSummaryResponse;
import com.module06.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* comment.
    FR-AC-01,02,03,04,05,09 — 개인 액션·체크리스트·회의별 조회 API 진입점.
    담당 엔드포인트
    - POST   /api/actions                                수동 추가 (예외 경로, MEMBER+)
    - GET    /api/actions                                내 액션 목록 조회 (호출자 본인 소유분만)
    - GET    /api/actions/{actionId}                     상세 조회 (전 구성원)
    - PATCH  /api/actions/{actionId}                     상태 변경 (담당자 본인)
    - PATCH  /api/actions/status/bulk                     보드 저장 시 일괄 상태 변경 (담당자 본인)
    - PATCH  /api/actions/{actionId}/review               AI 검토 확인·수정 (담당자 본인)
    - POST   /api/actions/{actionId}/checklist            체크리스트 추가 (담당자 본인)
    - PATCH  /api/actions/{actionId}/checklist/{itemId}   체크리스트 수정 (담당자 본인)
    - DELETE /api/actions/{actionId}/checklist/{itemId}   체크리스트 삭제 (담당자 본인)
    - GET    /api/meetings/{meetingId}/actions             회의별 액션 조회 (전 구성원) — 유일하게
      base path가 /api/actions가 아니다. 회의(D) 상세 화면 전용 조회라 회의 리소스 하위에 둔다.
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).
    지금은 수동 추가(POST /api/actions)만 배선한다 — 나머지는 각 유스케이스 착수 시 추가.

    연결된 클래스
    - CreateActionUseCase · GetMyActionsUseCase · GetActionDetailUseCase · UpdateActionStatusUseCase ·
      BulkUpdateActionStatusUseCase · ReviewActionUseCase · GetActionsByMeetingUseCase · ChecklistItemUseCase : 호출 대상
    - CreateActionRequest · UpdateActionStatusRequest · BulkUpdateActionStatusRequest ·
      ReviewActionRequest · CreateChecklistItemRequest · UpdateChecklistItemRequest        : 입력 DTO
    - ActionSummaryResponse · ActionDetailResponse · ChecklistItemResponse                 : 출력 DTO
    - ApiResponse                                                                          : 성공 응답 래퍼
*/
@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final CreateActionUseCase createActionUseCase;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ActionSummaryResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody CreateActionRequest request
    ) {
        Action action = createActionUseCase.create(new CreateActionCommand(
                companyId,
                request.projectId(),
                request.actionType(),
                request.teamId(),
                request.assigneeMemberId(),
                request.title(),
                request.description(),
                request.dueDate()
        ));

        return ApiResponse.created("액션을 추가했습니다.", ActionSummaryResponse.from(action));
    }
}
