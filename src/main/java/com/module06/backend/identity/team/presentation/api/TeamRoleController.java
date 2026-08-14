package com.module06.backend.identity.team.presentation.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.module06.backend.identity.team.application.command.CreateTeamRoleCommand;
import com.module06.backend.identity.team.application.command.RenameTeamRoleCommand;
import com.module06.backend.identity.team.application.dto.RoleNode;
import com.module06.backend.identity.team.application.usecase.CreateTeamRoleUseCase;
import com.module06.backend.identity.team.application.usecase.DeleteTeamRoleUseCase;
import com.module06.backend.identity.team.application.usecase.RenameTeamRoleUseCase;
import com.module06.backend.identity.team.presentation.api.dto.request.CreateTeamRoleRequest;
import com.module06.backend.identity.team.presentation.api.dto.request.RenameTeamRoleRequest;
import com.module06.backend.identity.team.presentation.api.dto.response.RoleResponse;

import lombok.RequiredArgsConstructor;

/**
 * 부서 안 "역할" CRUD(§6-10~6-12).
 *
 * <p>조회 엔드포인트가 없다 — 역할 목록은 {@code GET /api/teams} 응답의 {@code roles} 가 이미
 * 싣고 있다(부서마다 왕복하지 않으려고 그렇게 뒀다). 여기 세 개는 그 목록을 바꾸는 창구고,
 * 응답도 같은 모양({@link RoleResponse})이라 화면이 목록을 다시 받지 않고 끼워 넣을 수 있다.
 *
 * <p>부서 CRUD(§6-2~6-4)와 같은 {@code OWNER} 전용이다 — 같은 화면(기업 설정 · 부서 체계 탭)의
 * 같은 조작이라 권한이 갈리면 화면 절반만 저장되는 상태가 생긴다.
 */
@Tag(name = "Identity", description = "부서 안 역할(Role) CRUD API")
@RestController
@RequestMapping("/api/teams/{teamId}/roles")
@RequiredArgsConstructor
public class TeamRoleController {

    private final CreateTeamRoleUseCase createTeamRoleUseCase;
    private final RenameTeamRoleUseCase renameTeamRoleUseCase;
    private final DeleteTeamRoleUseCase deleteTeamRoleUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<RoleResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTeamRoleRequest request) {
        RoleNode node = createTeamRoleUseCase.create(
                new CreateTeamRoleCommand(companyId, teamId, request.name()));
        return ApiResponse.created("역할을 생성했습니다", RoleResponse.from(node));
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<RoleResponse> rename(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamId,
            @PathVariable Long roleId,
            @Valid @RequestBody RenameTeamRoleRequest request) {
        RoleNode node = renameTeamRoleUseCase.rename(
                new RenameTeamRoleCommand(companyId, teamId, roleId, request.name()));
        return ApiResponse.success("역할 이름을 수정했습니다", RoleResponse.from(node));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long teamId,
            @PathVariable Long roleId) {
        deleteTeamRoleUseCase.delete(companyId, teamId, roleId);
        return ApiResponse.successWithoutData("역할을 삭제했습니다");
    }
}
