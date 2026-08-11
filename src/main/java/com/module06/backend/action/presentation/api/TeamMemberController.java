package com.module06.backend.action.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase;
import com.module06.backend.action.presentation.api.response.TeamMemberStatusResponse;
import com.module06.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/* comment.
    2026-08-11, 이슈 #352 — 팀 대시보드 "팀원 현황" API 진입점. base path = /api/team/members.
    TeamActionController(팀 액션)와 별도 컨트롤러인 이유는 TeamMemberStatusService 주석과 동일
    (다루는 리소스가 다르다).

    연결된 클래스
    - GetTeamMemberStatusUseCase   : 호출 대상
    - TeamMemberStatusResponse     : 출력 DTO
*/
@RestController
@RequestMapping("/api/team/members")
@RequiredArgsConstructor
@Tag(name = "Team Member", description = "팀원 현황 API")
public class TeamMemberController {

    private final GetTeamMemberStatusUseCase getTeamMemberStatusUseCase;

    // teamId는 JWT에서만 꺼낸다 — 헤더로 받으면 다른 팀 로스터를 조회할 수 있다.
    @Operation(summary = "팀원 현황 조회", description = "JWT teamId로 스코프된 LEADER 전용. "
            + "이름·직급·역할·재직상태·담당 액션 수.")
    @GetMapping
    @PreAuthorize("hasRole('LEADER')")
    public ApiResponse<List<TeamMemberStatusResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId
    ) {
        var result = getTeamMemberStatusUseCase.getTeamMemberStatus(teamId);

        return ApiResponse.success("팀원 현황을 조회했습니다.", TeamMemberStatusResponse.from(result));
    }
}
