package com.module06.backend.action.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase;
import com.module06.backend.action.application.usecase.GetTeamDashboardSummaryUseCase;
import com.module06.backend.action.application.usecase.IssueTeamActionAttachmentDownloadUrlUseCase;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.presentation.api.response.ActionSummaryResponse;
import com.module06.backend.action.presentation.api.response.TeamActionDetailResponse;
import com.module06.backend.action.presentation.api.response.TeamDashboardSummaryResponse;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.response.PageResponse;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;

import lombok.RequiredArgsConstructor;

/* comment.
    FR-AC-06,08 — 팀 액션 API 진입점. base path = /api/team/actions.
    담당 엔드포인트
    - GET    /api/team/actions                          팀 액션 목록 (JWT teamId로 스코프된 LEADER 전용)
    - GET    /api/team/actions/{teamActionId}            상세 (전 구성원, 프로젝트 첨부파일 포함)
    - GET    /api/team/actions/{teamActionId}?tab=timeline  하위 개인 액션 타임라인 (전 구성원)
    - GET    /api/team/actions/{teamActionId}/attachments/{attachmentId}/download-url  첨부파일 다운로드 URL 발급 (전 구성원, 2026-08-10)
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
@Tag(name = "Team Action", description = "팀 액션 API")
public class TeamActionController {

    private final GetTeamActionsUseCase getTeamActionsUseCase;
    private final GetTeamActionDetailUseCase getTeamActionDetailUseCase;
    private final GetTeamActionTimelineUseCase getTeamActionTimelineUseCase;
    private final IssueTeamActionAttachmentDownloadUrlUseCase issueTeamActionAttachmentDownloadUrlUseCase;
    private final GetTeamDashboardSummaryUseCase getTeamDashboardSummaryUseCase;

    // 팀 액션 목록 — teamId는 토큰에서만 꺼낸다(헤더로 받으면 남의 팀을 조회할 수 있다).
    // 2026-08-10 페이지네이션+필터+정렬 도입(이홍근 요청) — page 0부터 시작, size 기본 20.
    @Operation(summary = "팀 액션 목록 조회", description = "JWT teamId로 스코프된 LEADER 전용. "
            + "페이지네이션(page/size), status 필터, 정렬(sort=dueDate|createdAt, order=asc|desc).")
    @GetMapping
    @PreAuthorize("hasRole('LEADER')")
    public ApiResponse<PageResponse<ActionSummaryResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = getTeamActionsUseCase.getTeamActions(teamId, status, sort, order, page, size);
        List<ActionSummaryResponse> items = result.items().stream()
                .map(ActionSummaryResponse::from)
                .toList();

        return ApiResponse.success("팀 액션 목록을 조회했습니다.",
                PageResponse.of(items, page, size, result.totalElements()));
    }

    // 팀 액션 상세 — 전 구성원 공개, companyId만 토큰에서 확인한다(IDOR 방지).
    // OpenAPI는 동일 path+method에 operation 하나만 허용해 params="tab=timeline" 분기를
    // 별도로 문서화할 수 없다(코드래빗 지적, 2026-08-09) — 두 응답 형태를 이 operation
    // 설명 하나에 같이 적는다.
    @Operation(summary = "팀 액션 상세 조회", description = "전 구성원 공개, 프로젝트 첨부파일 인라인 포함. "
            + "?tab=timeline을 주면 같은 경로에서 하위 개인 액션 타임라인 목록을 대신 반환한다.")
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

    // 2026-08-10, 이홍근 요청 — 상세 응답에 인라인으로 뜨는 첨부파일의 다운로드 URL 발급. 전 구성원 공개.
    @Operation(summary = "팀 액션 첨부파일 다운로드 URL 발급", description = "전 구성원 공개. presigned GET URL 하나 발급, 5분 만료.")
    @GetMapping("/{teamActionId}/attachments/{attachmentId}/download-url")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IssuedDownloadUrl> issueAttachmentDownloadUrl(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamActionId,
            @PathVariable Long attachmentId
    ) {
        IssuedDownloadUrl issuedDownloadUrl =
                issueTeamActionAttachmentDownloadUrlUseCase.issueAttachmentDownloadUrl(companyId, teamActionId, attachmentId);

        return ApiResponse.success("다운로드 URL이 발급되었습니다.", issuedDownloadUrl);
    }

    // 2026-08-11, 이슈 #352 — 팀 대시보드 KPI 4종. teamId·memberId 둘 다 JWT에서만 꺼낸다
    // (다른 팀·다른 사람 집계를 헤더로 요청할 수 없게).
    @Operation(summary = "팀 대시보드 요약 조회", description = "JWT teamId로 스코프된 LEADER 전용. "
            + "팀 액션(진행중)·팀원 액션(팀 소속 개인 액션 전체)·내 액션(처리예정)·완료 액션(내 누적).")
    @GetMapping("/dashboard-summary")
    @PreAuthorize("hasRole('LEADER')")
    public ApiResponse<TeamDashboardSummaryResponse> getTeamDashboardSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        var result = getTeamDashboardSummaryUseCase.getTeamDashboardSummary(teamId, memberId);

        return ApiResponse.success("팀 대시보드 요약을 조회했습니다.", TeamDashboardSummaryResponse.from(result));
    }
}
