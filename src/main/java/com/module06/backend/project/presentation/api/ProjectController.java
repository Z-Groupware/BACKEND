package com.module06.backend.project.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.presentation.api.request.CreateProjectRequest;
import com.module06.backend.project.presentation.api.response.ProjectSummaryResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* comment.
    FR-PJ-01,02 — 프로젝트 생성·목록조회 엔드포인트. 나머지(상세·수정·벌크·타임라인)는 다음 차례.
*/
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final GetProjectListUseCase getProjectListUseCase;

    /*
        회사·작성자를 토큰에서 꺼낸다. 헤더로 받으면 로그인만 한 사람이 남의 회사 번호를 적어
        보낼 수 있어서, 인증을 걸어도 막히지 않는 구멍이 된다.
    */
    @PostMapping
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
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ProjectSummaryResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId
    ) {
        List<ProjectSummaryResponse> response = getProjectListUseCase.list(companyId).stream()
                .map(ProjectSummaryResponse::from)
                .toList();

        return ApiResponse.success("프로젝트 목록을 조회했습니다.", response);
    }
}
