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

    @GetMapping
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
