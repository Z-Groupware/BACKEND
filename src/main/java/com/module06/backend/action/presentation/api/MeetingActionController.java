package com.module06.backend.action.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase;
import com.module06.backend.action.presentation.api.response.ActionSummaryResponse;
import com.module06.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/* comment.
    FR-AC-09 — 회의별 액션 조회 API 진입점. base path가 /api/actions가 아니라
    /api/meetings인 이유: 회의(D) 상세 화면 전용 조회라 회의 리소스 하위에 둔다
    (ActionController 헤더 주석에 이미 예고돼 있던 예외).

    담당 엔드포인트
    - GET /api/meetings/{meetingId}/actions   회의별 액션 전체 조회 (전 구성원, TEAM·PERSONAL 혼재)

    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    연결된 클래스
    - GetActionsByMeetingUseCase : 호출 대상
    - ActionSummaryResponse      : 출력 DTO
    - ApiResponse                : 성공 응답 래퍼
*/
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Tag(name = "Action", description = "개인 액션 API")
public class MeetingActionController {

    private final GetActionsByMeetingUseCase getActionsByMeetingUseCase;

    // 전 구성원 공개 — companyId는 토큰에서만 확인한다(IDOR 방지, ActionController.detail과 동일 판단).
    @Operation(summary = "회의별 액션 조회", description = "회의 상세 화면 전용, TEAM·PERSONAL 혼재 반환.")
    @GetMapping("/{meetingId}/actions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ActionSummaryResponse>> listByMeeting(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long meetingId
    ) {
        List<ActionSummaryResponse> response = getActionsByMeetingUseCase.getActionsByMeeting(companyId, meetingId)
                .stream()
                .map(ActionSummaryResponse::from)
                .toList();

        return ApiResponse.success("회의별 액션을 조회했습니다.", response);
    }
}
