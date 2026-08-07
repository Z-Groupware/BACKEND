package com.module06.backend.identity.member.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberPage;
import com.module06.backend.identity.member.application.dto.OrgChartTeam;
import com.module06.backend.identity.member.application.usecase.GetMemberDetailUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberOrgChartUseCase;
import com.module06.backend.identity.member.application.usecase.GetMembersUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberAdminUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberRoleUseCase;
import com.module06.backend.identity.member.presentation.api.dto.request.UpdateMemberAdminRequest;
import com.module06.backend.identity.member.presentation.api.dto.request.UpdateMemberRoleRequest;
import com.module06.backend.identity.member.presentation.api.dto.response.MemberDetailResponse;
import com.module06.backend.identity.member.presentation.api.dto.response.MemberPageResponse;
import com.module06.backend.identity.member.presentation.api.dto.response.OrgChartTeamResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "구성원 관리 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final GetMembersUseCase getMembersUseCase;
    private final GetMemberOrgChartUseCase getMemberOrgChartUseCase;
    private final GetMemberDetailUseCase getMemberDetailUseCase;
    private final UpdateMemberRoleUseCase updateMemberRoleUseCase;
    private final UpdateMemberAdminUseCase updateMemberAdminUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<MemberPageResponse> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @RequestParam(defaultValue = "ALL") MemberListFilter filter,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MemberPage result = getMembersUseCase.getMembers(companyId, filter, q, page, size);
        return ApiResponse.success("구성원 목록을 조회했습니다", MemberPageResponse.from(result));
    }

    @GetMapping("/org-chart")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<List<OrgChartTeamResponse>> orgChart(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        List<OrgChartTeamResponse> response = getMemberOrgChartUseCase.getOrgChart(companyId).stream()
                .map(OrgChartTeamResponse::from)
                .toList();
        return ApiResponse.success("조직도를 조회했습니다", response);
    }

    @GetMapping("/{memberId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<MemberDetailResponse> detail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long memberId) {
        MemberDetail detail = getMemberDetailUseCase.getDetail(companyId, memberId);
        return ApiResponse.success("구성원 상세를 조회했습니다", MemberDetailResponse.from(detail));
    }

    @PatchMapping("/{memberId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<MemberDetailResponse> updateRole(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long actingMemberId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        if (request.isAdmin() != null) {
            throw new BusinessException(AuthErrorCode.MEMBER_FIELD_NOT_ALLOWED);
        }
        MemberDetail detail = updateMemberRoleUseCase.update(new UpdateMemberRoleCommand(
                companyId, actingMemberId, memberId, request.role(), request.jobPositionId()));
        return ApiResponse.success("구성원 정보를 수정했습니다", MemberDetailResponse.from(detail));
    }

    @PatchMapping("/{memberId}/admin")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<MemberDetailResponse> updateAdmin(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberAdminRequest request) {
        MemberDetail detail = updateMemberAdminUseCase.update(new UpdateMemberAdminCommand(
                companyId, memberId, request.isAdmin()));
        return ApiResponse.success("관리 권한을 변경했습니다", MemberDetailResponse.from(detail));
    }
}
