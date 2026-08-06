package com.module06.backend.identity.team.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.team.application.usecase.GetTeamTreeUseCase;
import com.module06.backend.identity.team.presentation.api.dto.response.TeamNodeResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "부서(Team) CRUD API")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final GetTeamTreeUseCase getTeamTreeUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TeamNodeResponse>> tree(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        List<TeamNodeResponse> response = getTeamTreeUseCase.getTree(companyId).stream()
                .map(TeamNodeResponse::from)
                .toList();
        return ApiResponse.success("부서 목록을 조회했습니다", response);
    }
}
