package com.module06.backend.identity.member.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberListItem;
import com.module06.backend.identity.member.application.dto.MemberPage;
import com.module06.backend.identity.member.application.dto.OrgChartMember;
import com.module06.backend.identity.member.application.dto.OrgChartSubTeam;
import com.module06.backend.identity.member.application.dto.OrgChartTeam;
import com.module06.backend.identity.member.application.dto.TeamLeaderStatus;
import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase.MemberDashboardSummary;
import com.module06.backend.identity.member.application.usecase.GetMemberDetailUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberOrgChartUseCase;
import com.module06.backend.identity.member.application.usecase.GetMembersUseCase;
import com.module06.backend.identity.member.application.usecase.GetTeamLeadersStatusUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberAdminUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberRoleUseCase;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;

/*
 * 구성원 관리 화면(§7)이 쓰는 키를 고정한다. 상세·역할변경·어드민토글 셋이 같은 응답 모양이라
 * 프론트가 파서 하나로 처리하는데, 하나만 달라지면 그 화면이 조용히 깨진다.
 *
 * 권한(role)과 역할 라벨(roleLabel)은 다른 값이다 — 전자가 인가 축이고 후자는 표시용이라,
 * 둘이 뒤바뀌면 화면에 "LEADER"가 역할 칸에 찍힌다.
 */
@DisplayName("MemberController")
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMembersUseCase getMembersUseCase;
    @MockitoBean
    private GetMemberOrgChartUseCase getMemberOrgChartUseCase;
    @MockitoBean
    private GetMemberDetailUseCase getMemberDetailUseCase;
    @MockitoBean
    private UpdateMemberRoleUseCase updateMemberRoleUseCase;
    @MockitoBean
    private UpdateMemberAdminUseCase updateMemberAdminUseCase;
    @MockitoBean
    private GetMemberDashboardSummaryUseCase getMemberDashboardSummaryUseCase;
    @MockitoBean
    private GetTeamLeadersStatusUseCase getTeamLeadersStatusUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /* ── 목록 ──────────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("목록 기본값 — filter=ALL · page=0 · size=20, 검색어는 없으면 null")
    void listUsesDefaults() throws Exception {
        authenticateAs(1L);
        when(getMembersUseCase.getMembers(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(page());

        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk());

        verify(getMembersUseCase).getMembers(1L, MemberListFilter.ALL, null, 0, 20);
    }

    @Test
    @DisplayName("목록 쿼리 파라미터를 그대로 넘긴다 — filter·q·page·size")
    void listPassesQueryParamsThrough() throws Exception {
        authenticateAs(1L);
        when(getMembersUseCase.getMembers(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(page());

        mockMvc.perform(get("/api/members")
                        .param("filter", "LEAVE_PENDING")
                        .param("q", "이하윤")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(getMembersUseCase).getMembers(1L, MemberListFilter.LEAVE_PENDING, "이하윤", 2, 50);
    }

    @Test
    @DisplayName("목록 응답 키 — totalElements·totalPages·hasNext·page·size·content[], 행에 role 과 roleLabel 이 따로 있다")
    void listResponseKeys() throws Exception {
        authenticateAs(1L);
        when(getMembersUseCase.getMembers(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(page());

        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalCount").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[0].memberId").value(3))
                .andExpect(jsonPath("$.data.content[0].name").value("이하윤"))
                .andExpect(jsonPath("$.data.content[0].teamName").value("개발팀"))
                .andExpect(jsonPath("$.data.content[0].positionName").value("선임"))
                .andExpect(jsonPath("$.data.content[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.content[0].isAdmin").value(false))
                .andExpect(jsonPath("$.data.content[0].roleLabel").value("프론트엔드"))
                .andExpect(jsonPath("$.data.content[0].workStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[0].joinedOn").value("2022-05-10"));
    }

    @Test
    @DisplayName("page 가 음수거나 size 가 상한을 넘으면 400 — 500 이 아니다")
    void invalidPagingIsRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(get("/api/members").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/members").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    /* ── 조직도 ────────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("조직도는 부서 → 역할 → 구성원 3단으로 내려간다")
    void orgChartIsThreeLevels() throws Exception {
        authenticateAs(1L);
        when(getMemberOrgChartUseCase.getOrgChart(1L)).thenReturn(List.of(
                new OrgChartTeam(2L, "개발팀", List.of(
                        new OrgChartSubTeam("프론트엔드", List.of(
                                new OrgChartMember(3L, "이하윤", "선임", Authority.MEMBER)))))));

        mockMvc.perform(get("/api/members/org-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamId").value(2))
                .andExpect(jsonPath("$.data[0].name").value("개발팀"))
                .andExpect(jsonPath("$.data[0].subTeams[0].roleLabel").value("프론트엔드"))
                .andExpect(jsonPath("$.data[0].subTeams[0].members[0].memberId").value(3))
                .andExpect(jsonPath("$.data[0].subTeams[0].members[0].name").value("이하윤"))
                .andExpect(jsonPath("$.data[0].subTeams[0].members[0].positionName").value("선임"))
                .andExpect(jsonPath("$.data[0].subTeams[0].members[0].role").value("MEMBER"));
    }

    /* ── 오너 대시보드 ─────────────────────────────────────────────────────── */

    @Test
    @DisplayName("인원 요약 응답 키 — totalMemberCount·onLeaveMemberCount 둘뿐이다")
    void dashboardSummaryResponseKeys() throws Exception {
        authenticateAs(1L);
        when(getMemberDashboardSummaryUseCase.getDashboardSummary(1L))
                .thenReturn(new MemberDashboardSummary(24L, 2L));

        mockMvc.perform(get("/api/members/dashboard-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMemberCount").value(24))
                .andExpect(jsonPath("$.data.onLeaveMemberCount").value(2));
    }

    /*
     * 휴직 기간은 "8월 1일~15일" 같은 완성된 문자열이 아니라 ISO 날짜 원자값이어야 한다 — 표시
     * 포맷은 프론트 몫이라, 여기서 문자열로 바뀌면 포맷을 바꿀 때마다 이 API 를 다시 건드리게 된다.
     */
    @Test
    @DisplayName("팀장 현황 응답 키 8개 — 휴직 기간은 ISO 날짜고, 재직 중이면 둘 다 null 이다")
    void leadersStatusResponseKeys() throws Exception {
        authenticateAs(1L);
        when(getTeamLeadersStatusUseCase.getTeamLeadersStatus(1L)).thenReturn(List.of(
                new TeamLeaderStatus(12L, "김서준", "seojun.kim@zteam.io", 2L, "개발팀",
                        MemberStatus.ACTIVE, null, null),
                new TeamLeaderStatus(31L, "강서연", "seoyeon.kang@zteam.io", 5L, "디자인팀",
                        MemberStatus.VACATION, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15))));

        mockMvc.perform(get("/api/members/leaders-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].memberId").value(12))
                .andExpect(jsonPath("$.data[0].name").value("김서준"))
                .andExpect(jsonPath("$.data[0].email").value("seojun.kim@zteam.io"))
                .andExpect(jsonPath("$.data[0].teamId").value(2))
                .andExpect(jsonPath("$.data[0].teamName").value("개발팀"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].leaveStartDate").doesNotExist())
                .andExpect(jsonPath("$.data[0].leaveEndDate").doesNotExist())
                .andExpect(jsonPath("$.data[1].status").value("VACATION"))
                .andExpect(jsonPath("$.data[1].leaveStartDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data[1].leaveEndDate").value("2026-08-15"));
    }

    /*
     * 두 경로가 상세 조회의 {memberId} 에 먹히면 안 된다. 먹히면 memberId 를 Long 으로 못 바꿔
     * 400 이 나가는데, 화면에서는 "대시보드가 가끔 안 뜬다"로만 보여 원인을 찾기 어렵다.
     */
    @Test
    @DisplayName("대시보드 경로는 상세 조회의 {memberId} 로 빨려들어가지 않는다")
    void dashboardPathsAreNotTreatedAsMemberId() throws Exception {
        authenticateAs(1L);
        when(getMemberDashboardSummaryUseCase.getDashboardSummary(1L))
                .thenReturn(new MemberDashboardSummary(0L, 0L));
        when(getTeamLeadersStatusUseCase.getTeamLeadersStatus(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/members/dashboard-summary")).andExpect(status().isOk());
        mockMvc.perform(get("/api/members/leaders-status")).andExpect(status().isOk());

        verify(getMemberDetailUseCase, never()).getDetail(anyLong(), anyLong());
    }

    /* ── 상세 · 역할변경 · 어드민토글 ─────────────────────────────────────────── */

    @Test
    @DisplayName("상세 응답 키 12개 — teamId·jobPositionId 같은 id 도 함께 준다(수정 폼이 쓴다)")
    void detailResponseKeys() throws Exception {
        authenticateAs(1L);
        when(getMemberDetailUseCase.getDetail(1L, 3L)).thenReturn(detail());

        mockMvc.perform(get("/api/members/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(3))
                .andExpect(jsonPath("$.data.name").value("이하윤"))
                .andExpect(jsonPath("$.data.teamId").value(2))
                .andExpect(jsonPath("$.data.teamName").value("개발팀"))
                .andExpect(jsonPath("$.data.jobPositionId").value(4))
                .andExpect(jsonPath("$.data.positionName").value("선임"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.isAdmin").value(false))
                .andExpect(jsonPath("$.data.roleLabel").value("프론트엔드"))
                .andExpect(jsonPath("$.data.workStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.email").value("hayun@zgroup.co.kr"))
                .andExpect(jsonPath("$.data.joinedOn").value("2022-05-10"));
    }

    @Test
    @DisplayName("역할변경은 role·jobPositionId 를 넘기고 대상·행위자를 경로와 토큰에서 정한다")
    void updateRolePassesKeysThrough() throws Exception {
        authenticateAs(1L);
        when(updateMemberRoleUseCase.update(any())).thenReturn(detail());

        mockMvc.perform(patch("/api/members/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEADER","jobPositionId":6}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateMemberRoleCommand> captor = ArgumentCaptor.forClass(UpdateMemberRoleCommand.class);
        verify(updateMemberRoleUseCase).update(captor.capture());
        UpdateMemberRoleCommand command = captor.getValue();

        assertThat(command.companyId()).isEqualTo(1L);
        assertThat(command.targetMemberId()).isEqualTo(3L);
        assertThat(command.role()).isEqualTo(Authority.LEADER);
        assertThat(command.jobPositionId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("역할변경 요청에 isAdmin 이 섞이면 400 — 어드민 부여는 이 경로가 아니다")
    void isAdminInRoleUpdateIsRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(patch("/api/members/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEADER","jobPositionId":6,"isAdmin":true}
                                """))
                .andExpect(status().isBadRequest());

        verify(updateMemberRoleUseCase, never()).update(any());
    }

    @Test
    @DisplayName("어드민 토글은 isAdmin 하나만 받고, 응답은 상세와 같은 모양이다")
    void updateAdminSharesDetailShape() throws Exception {
        authenticateAs(1L);
        when(updateMemberAdminUseCase.update(any())).thenReturn(detail());

        mockMvc.perform(patch("/api/members/3/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isAdmin":true}
                                """))
                .andExpect(status().isOk())
                /* 상세·역할변경과 키가 같아야 프론트가 파서 하나로 처리한다. */
                .andExpect(jsonPath("$.data.memberId").value(3))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.isAdmin").value(false))
                .andExpect(jsonPath("$.data.roleLabel").value("프론트엔드"))
                .andExpect(jsonPath("$.data.joinedOn").value("2022-05-10"));

        ArgumentCaptor<UpdateMemberAdminCommand> captor = ArgumentCaptor.forClass(UpdateMemberAdminCommand.class);
        verify(updateMemberAdminUseCase).update(captor.capture());
        UpdateMemberAdminCommand command = captor.getValue();

        assertThat(command.companyId()).isEqualTo(1L);
        assertThat(command.targetMemberId()).isEqualTo(3L);
        assertThat(command.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("어드민 토글에 isAdmin 을 빼면 400")
    void missingIsAdminIsRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(patch("/api/members/3/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(updateMemberAdminUseCase, never()).update(any());
    }

    /* ── 픽스처 ────────────────────────────────────────────────────────────── */

    private static MemberPage page() {
        return MemberPage.of(1L, 0, 20, List.of(
                new MemberListItem(3L, "이하윤", "개발팀", "선임",
                        Authority.MEMBER, false, "프론트엔드",
                        MemberStatus.ACTIVE, LocalDate.of(2022, 5, 10))));
    }

    private static MemberDetail detail() {
        return new MemberDetail(3L, "이하윤", 2L, "개발팀", 4L, "선임",
                Authority.MEMBER, false, "프론트엔드", MemberStatus.ACTIVE,
                "hayun@zgroup.co.kr", LocalDate.of(2022, 5, 10));
    }

    private void authenticateAs(Long companyId) {
        AuthPrincipal principal = new AuthPrincipal(9L, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
