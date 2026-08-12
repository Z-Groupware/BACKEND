package com.module06.backend.project.presentation.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.response.PageResponse;
import com.module06.backend.project.application.command.BulkUpdateProjectStatusCommand;
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.command.UpdateProjectCommand;
import com.module06.backend.project.application.usecase.BulkUpdateProjectStatusUseCase;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetOwnerDashboardSummaryUseCase;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase;
import com.module06.backend.project.application.usecase.UpdateProjectUseCase;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.presentation.api.request.BulkUpdateProjectStatusRequest;
import com.module06.backend.project.presentation.api.request.CreateProjectRequest;
import com.module06.backend.project.presentation.api.request.UpdateProjectRequest;
import com.module06.backend.project.presentation.api.response.OwnerDashboardSummaryResponse;
import com.module06.backend.project.presentation.api.response.ProjectDetailResponse;
import com.module06.backend.project.presentation.api.response.ProjectSummaryResponse;
import com.module06.backend.project.presentation.api.response.ProjectTimelineItemResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* comment.
    FR-PJ-01,02,03,06 — 프로젝트 생성·목록조회·상세조회·수정·보드 상태 일괄변경 엔드포인트.
    타임라인(FR-PJ-07)만 다음 차례.
*/
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Project", description = "프로젝트 API")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final GetProjectListUseCase getProjectListUseCase;
    private final GetProjectDetailUseCase getProjectDetailUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final BulkUpdateProjectStatusUseCase bulkUpdateProjectStatusUseCase;
    private final GetProjectTimelineUseCase getProjectTimelineUseCase;
    private final GetOwnerDashboardSummaryUseCase getOwnerDashboardSummaryUseCase;

    /*
        회사·작성자를 토큰에서 꺼낸다. 헤더로 받으면 로그인만 한 사람이 남의 회사 번호를 적어
        보낼 수 있어서, 인증을 걸어도 막히지 않는 구멍이 된다.
    */
    @Operation(summary = "프로젝트 생성", description = "OWNER 전용.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<ProjectSummaryResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        Project project = createProjectUseCase.create(new CreateProjectCommand(
                companyId,
                memberId,
                request.tag(),
                request.name(),
                request.description(),
                request.color(),
                request.startDate(),
                request.dueDate(),
                request.teamIds()
        ));

        return ApiResponse.created("프로젝트를 생성했습니다.", ProjectSummaryResponse.from(project));
    }

    /*
        권한표상 목록·기획 열람은 전원이므로 역할을 좁히지 않되, 인증은 요구한다.

        지금은 이 애너테이션이 실제로 동작할 기회가 없다 — 익명 요청은 인자 해석 단계에서
        먼저 터진다(principal 이 문자열 "anonymousUser" 라 expression 평가 실패). 회의·회의실 API 도
        같은 상태다. Task 10 이 anyRequest().authenticated() 로 뒤집으면 필터가 그 앞에서 막는다.
        그때 이 줄은 "이 엔드포인트는 로그인 전원용"이라는 의도 표시 + 2차 방어로 남는다.
    */
    // 2026-08-10 페이지네이션 도입(이홍근 요청) — page 0부터 시작, size 기본 20.
    // 2026-08-10 필터/정렬 추가(이홍근 요청) — status 필터(선택), sort/order 화이트리스트(dueDate·createdAt).
    @Operation(summary = "프로젝트 목록 조회", description = "전 구성원 공개, 회사 전체 프로젝트. "
            + "페이지네이션(page/size), status 필터, 정렬(sort=dueDate|createdAt, order=asc|desc).")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<ProjectSummaryResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = getProjectListUseCase.list(companyId, status, sort, order, page, size);
        List<ProjectSummaryResponse> items = result.items().stream()
                .map(ProjectSummaryResponse::from)
                .toList();

        return ApiResponse.success("프로젝트 목록을 조회했습니다.",
                PageResponse.of(items, page, size, result.totalElements()));
    }

    @Operation(summary = "프로젝트 상세 조회", description = "기획(description) 포함, 전 구성원 공개.")
    @GetMapping("/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProjectDetailResponse> getDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long projectId
    ) {
        var result = getProjectDetailUseCase.getDetail(companyId, projectId);

        return ApiResponse.success("프로젝트 상세를 조회했습니다.", ProjectDetailResponse.from(result));
    }

    @Operation(summary = "프로젝트 수정", description = "OWNER 전용. tag는 파라미터로 받지 않아 불변 보장.")
    @PatchMapping("/{projectId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<ProjectSummaryResponse> update(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        Project project = updateProjectUseCase.update(new UpdateProjectCommand(
                projectId,
                memberId,
                request.name(),
                request.description(),
                request.color(),
                request.startDate(),
                request.dueDate(),
                request.teamIds()
        ));

        return ApiResponse.success("프로젝트를 수정했습니다.", ProjectSummaryResponse.from(project));
    }

    @Operation(summary = "프로젝트 상태 일괄 변경", description = "보드 \"저장\" 버튼, OWNER 전용, all-or-nothing.")
    @PatchMapping("/status/bulk")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> bulkUpdateStatus(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody BulkUpdateProjectStatusRequest request
    ) {
        List<BulkUpdateProjectStatusCommand.Item> items = request.items().stream()
                .map(item -> new BulkUpdateProjectStatusCommand.Item(item.projectId(), item.status()))
                .toList();

        bulkUpdateProjectStatusUseCase.bulkUpdateStatus(new BulkUpdateProjectStatusCommand(memberId, items));

        return ApiResponse.successWithoutData("프로젝트 상태를 일괄 변경했습니다.");
    }

    @Operation(summary = "프로젝트 타임라인 조회", description = "이 프로젝트에 하달된 팀 액션 전체, 지연 여부 포함.")
    @GetMapping("/{projectId}/timeline")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ProjectTimelineItemResponse>> getTimeline(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long projectId
    ) {
        List<ProjectTimelineItemResponse> response = getProjectTimelineUseCase.getTimeline(companyId, projectId).stream()
                .map(ProjectTimelineItemResponse::from)
                .toList();

        return ApiResponse.success("프로젝트 타임라인을 조회했습니다.", response);
    }

    // 2026-08-11, 이슈 #352 — 오너 대시보드 KPI 카드 중 project(C) 소유분만. "전체 사원"·"휴직자"는
    // identity/leave(B) 소유라 이 응답에 없다 — FE가 각 도메인 응답을 화면에서 합쳐 4카드를 구성한다.
    @Operation(summary = "오너 대시보드 요약 조회", description = "OWNER 전용. 전체 프로젝트 수·마감 D-7 프로젝트 수.")
    @GetMapping("/dashboard-summary")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<OwnerDashboardSummaryResponse> getOwnerDashboardSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId
    ) {
        var result = getOwnerDashboardSummaryUseCase.getOwnerDashboardSummary(companyId);

        return ApiResponse.success("오너 대시보드 요약을 조회했습니다.", OwnerDashboardSummaryResponse.from(result));
    }
}
