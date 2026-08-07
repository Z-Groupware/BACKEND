package com.module06.backend.action.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;

import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase;
import com.module06.backend.action.presentation.api.response.ActionSummaryResponse;
import com.module06.backend.action.presentation.api.response.TeamActionDetailResponse;
import com.module06.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/* comment.
    FR-AC-06,08 — 팀 액션 API 진입점. base path = /api/team/actions.
    담당 엔드포인트
    - GET    /api/team/actions                          팀 액션 목록 (JWT teamId로 스코프된 LEADER 전용)
    - GET    /api/team/actions/{teamActionId}            상세 (전 구성원, 프로젝트 첨부파일 포함)
    - GET    /api/team/actions/{teamActionId}?tab=timeline  하위 개인 액션 타임라인 (전 구성원)
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    상세·타임라인이 같은 경로를 쓰는 이유(2026-08-07 이홍근 확정) — 별도 경로가 아니라
    같은 리소스의 다른 뷰. @GetMapping의 params 조건으로 분기한다: tab=timeline이면 더
    구체적인 매핑이 우선하고, 없으면 일반 상세로 떨어진다(Spring이 매핑 특정도로 라우팅).

    아직 배선하지 않은 것
    - 상태 변경 (FR-AC-07) — 2026-08-07 폐기 확정. 팀 액션은 보드에서 완전히 빠지므로 이
      엔드포인트 자체가 생기지 않는다. UpdateTeamActionStatusUseCase·BulkUpdateTeamActionStatusUseCase·
      TeamActionLeaderOnlyPolicy는 죽은 스켈레톤이라 함께 삭제했다(TeamActionService 주석 참고).

    연결된 클래스
    - GetTeamActionsUseCase · GetTeamActionDetailUseCase · GetTeamActionTimelineUseCase : 호출 대상
    - ActionSummaryResponse · TeamActionDetailResponse    : 출력 DTO
    - ApiResponse                                         : 성공 응답 래퍼
*/
@RestController
@RequestMapping("/api/team/actions")
@RequiredArgsConstructor
public class TeamActionController {

    private final GetTeamActionsUseCase getTeamActionsUseCase;
    private final GetTeamActionDetailUseCase getTeamActionDetailUseCase;
    private final GetTeamActionTimelineUseCase getTeamActionTimelineUseCase;

    // 팀 액션 목록 — teamId는 토큰에서만 꺼낸다(헤더로 받으면 남의 팀을 조회할 수 있다).
    @GetMapping
    @PreAuthorize("hasRole('LEADER')")
    public ApiResponse<List<ActionSummaryResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId
    ) {
        List<ActionSummaryResponse> response = getTeamActionsUseCase.getTeamActions(teamId).stream()
                .map(ActionSummaryResponse::from)
                .toList();

        return ApiResponse.success("팀 액션 목록을 조회했습니다.", response);
    }

    // 팀 액션 상세 — 전 구성원 공개, companyId만 토큰에서 확인한다(IDOR 방지).
    @GetMapping("/{teamActionId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TeamActionDetailResponse> detail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamActionId
    ) {
        TeamActionDetailResponse response = TeamActionDetailResponse.from(
                getTeamActionDetailUseCase.getTeamActionDetail(companyId, teamActionId));

        return ApiResponse.success("팀 액션 상세를 조회했습니다.", response);
    }

    // 팀 액션 타임라인 — 하위 개인 액션 목록, 전 구성원 공개. 상세와 같은 경로, tab=timeline일 때만 이쪽으로 라우팅된다.
    @GetMapping(value = "/{teamActionId}", params = "tab=timeline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ActionSummaryResponse>> timeline(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamActionId
    ) {
        List<ActionSummaryResponse> response = getTeamActionTimelineUseCase.getTeamActionTimeline(companyId, teamActionId)
                .stream()
                .map(ActionSummaryResponse::from)
                .toList();

        return ApiResponse.success("팀 액션 타임라인을 조회했습니다.", response);
    }
}
