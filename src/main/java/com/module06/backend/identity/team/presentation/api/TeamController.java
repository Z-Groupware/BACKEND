package com.module06.backend.identity.team.presentation.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.team.application.command.CreateTeamCommand;
import com.module06.backend.identity.team.application.command.RenameTeamCommand;
import com.module06.backend.identity.team.application.dto.TeamNode;
import com.module06.backend.identity.team.application.usecase.CreateTeamUseCase;
import com.module06.backend.identity.team.application.usecase.DeleteTeamUseCase;
import com.module06.backend.identity.team.application.usecase.GetTeamTreeUseCase;
import com.module06.backend.identity.team.application.usecase.RenameTeamUseCase;
import com.module06.backend.identity.team.presentation.api.dto.request.CreateTeamRequest;
import com.module06.backend.identity.team.presentation.api.dto.request.RenameTeamRequest;
import com.module06.backend.identity.team.presentation.api.dto.response.TeamNodeResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "부서(Team) CRUD API")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final GetTeamTreeUseCase getTeamTreeUseCase;
    private final CreateTeamUseCase createTeamUseCase;
    private final RenameTeamUseCase renameTeamUseCase;
    private final DeleteTeamUseCase deleteTeamUseCase;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<TeamNodeResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody CreateTeamRequest request) {
        TeamNode node = createTeamUseCase.create(
                new CreateTeamCommand(companyId, request.name(), request.parentTeamId()));
        return ApiResponse.created("부서를 생성했습니다", TeamNodeResponse.from(node));
    }

    @PatchMapping("/{teamId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<TeamNodeResponse> rename(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamId,
            @Valid @RequestBody RenameTeamRequest request) {
        TeamNode node = renameTeamUseCase.rename(new RenameTeamCommand(companyId, teamId, request.name()));
        return ApiResponse.success("부서 이름을 수정했습니다", TeamNodeResponse.from(node));
    }

    @DeleteMapping("/{teamId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamId) {
        deleteTeamUseCase.delete(companyId, teamId);
        return ApiResponse.successWithoutData("부서를 삭제했습니다");
    }
}
