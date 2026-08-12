package com.module06.backend.identity.member.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberPage;
import com.module06.backend.identity.member.application.dto.OrgChartTeam;
import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase.MemberDashboardSummary;
import com.module06.backend.identity.member.application.usecase.GetMemberDetailUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberOrgChartUseCase;
import com.module06.backend.identity.member.application.usecase.GetMembersUseCase;
import com.module06.backend.identity.member.application.usecase.GetTeamLeadersStatusUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberAdminUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberRoleUseCase;
import com.module06.backend.identity.member.presentation.api.dto.request.UpdateMemberAdminRequest;
import com.module06.backend.identity.member.presentation.api.dto.request.UpdateMemberRoleRequest;
import com.module06.backend.identity.member.presentation.api.dto.response.MemberDashboardSummaryResponse;
import com.module06.backend.identity.member.presentation.api.dto.response.MemberDetailResponse;
import com.module06.backend.identity.member.presentation.api.dto.response.MemberPageResponse;
import com.module06.backend.identity.member.presentation.api.dto.response.OrgChartTeamResponse;
import com.module06.backend.identity.member.presentation.api.dto.response.TeamLeaderStatusResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "구성원 관리 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Validated
public class MemberController {

    private final GetMembersUseCase getMembersUseCase;
    private final GetMemberOrgChartUseCase getMemberOrgChartUseCase;
    private final GetMemberDetailUseCase getMemberDetailUseCase;
    private final GetMemberDashboardSummaryUseCase getMemberDashboardSummaryUseCase;
    private final GetTeamLeadersStatusUseCase getTeamLeadersStatusUseCase;
    private final UpdateMemberRoleUseCase updateMemberRoleUseCase;
    private final UpdateMemberAdminUseCase updateMemberAdminUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<MemberPageResponse> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @RequestParam(defaultValue = "ALL") MemberListFilter filter,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        MemberPage result = getMembersUseCase.getMembers(companyId, filter, q, page, size);
        return ApiResponse.success("구성원 목록을 조회했습니다", MemberPageResponse.from(result));
    }

    /**
     * 조직도는 전 사원에게 연다. 회사 안에서 누가 어느 부서·직급인지는 명부가 아니라 안내에
     * 가깝고, 막아두면 사원이 결재선이나 인수인계 대상자를 찾을 수 없다.
     *
     * <p>목록({@link #list})은 계속 관리자 전용이다 — 같은 사람들을 담지만 이메일·어드민 겸직·
     * 대기 상태(휴직·오프보딩 신청 여부)까지 실려 나가고, 그건 인사 정보다. 조직도 응답에는
     * 이름·직급·권한만 들어간다.
     */
    @GetMapping("/org-chart")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<OrgChartTeamResponse>> orgChart(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        List<OrgChartTeamResponse> response = getMemberOrgChartUseCase.getOrgChart(companyId).stream()
                .map(OrgChartTeamResponse::from)
                .toList();
        return ApiResponse.success("조직도를 조회했습니다", response);
    }

    /** 오너 대시보드 KPI 카드 뒤 2개(전체 사원·휴직자). 앞 2개는 project 도메인이 낸다. */
    @GetMapping("/dashboard-summary")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<MemberDashboardSummaryResponse> dashboardSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        MemberDashboardSummary summary = getMemberDashboardSummaryUseCase.getDashboardSummary(companyId);
        return ApiResponse.success("대시보드 인원 요약을 조회했습니다", MemberDashboardSummaryResponse.from(summary));
    }

    /** 오너 대시보드 "팀장 현황" 테이블 — 팀마다 팀장 1행. 리더가 공석인 팀은 행이 빠진다. */
    @GetMapping("/leaders-status")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<List<TeamLeaderStatusResponse>> leadersStatus(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        List<TeamLeaderStatusResponse> response = getTeamLeadersStatusUseCase.getTeamLeadersStatus(companyId).stream()
                .map(TeamLeaderStatusResponse::from)
                .toList();
        return ApiResponse.success("팀장 현황을 조회했습니다", response);
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
                companyId, actingMemberId, memberId, request.role(), request.jobPositionId(),
                request.roleLabel()));
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
