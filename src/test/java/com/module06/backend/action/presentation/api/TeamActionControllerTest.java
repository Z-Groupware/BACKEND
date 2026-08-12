package com.module06.backend.action.presentation.api;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase.TeamActionDetail;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase.TimelineItem;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase.TeamActionListItem;
import com.module06.backend.action.application.usecase.GetTeamDashboardSummaryUseCase;
import com.module06.backend.action.application.usecase.GetTeamDashboardSummaryUseCase.TeamDashboardSummary;
import com.module06.backend.action.application.usecase.IssueTeamActionAttachmentDownloadUrlUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TeamActionController")
@WebMvcTest(TeamActionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamActionControllerTest {

    private static final Long TEAM = 7L;
    private static final Long COMPANY = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTeamActionsUseCase getTeamActionsUseCase;

    @MockitoBean
    private GetTeamActionDetailUseCase getTeamActionDetailUseCase;

    @MockitoBean
    private GetTeamActionTimelineUseCase getTeamActionTimelineUseCase;

    @MockitoBean
    private IssueTeamActionAttachmentDownloadUrlUseCase issueTeamActionAttachmentDownloadUrlUseCase;

    @MockitoBean
    private GetTeamDashboardSummaryUseCase getTeamDashboardSummaryUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("목록은 LEADER 권한이면 토큰의 teamId로 조회한다")
    void listUsesTeamIdFromTokenWhenLeader() throws Exception {
        authenticateAs(1L, COMPANY, TEAM, "LEADER");
        when(getTeamActionsUseCase.getTeamActions(eq(TEAM), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetTeamActionsUseCase.TeamActionListResult(
                        List.of(new TeamActionListItem(teamAction(), "GOODS", "굿즈", "개발팀", 2, 5)), 1L));

        mockMvc.perform(get("/api/team/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].projectTag").value("GOODS"))
                .andExpect(jsonPath("$.data.content[0].teamName").value("개발팀"))
                .andExpect(jsonPath("$.data.content[0].childDoneCount").value(2))
                .andExpect(jsonPath("$.data.content[0].childTotalCount").value(5));
    }

    // LEADER 외 접근 차단(@PreAuthorize)은 @WebMvcTest 슬라이스에 SecurityConfig(@EnableMethodSecurity)가
    // 안 실려서 이 슬라이스로는 검증이 안 된다 — 이 레포의 다른 컨트롤러 테스트도 같은 이유로
    // 권한 경계는 안 다루고 배선(생성자 주입 값 전달)만 검증한다(ActionControllerTest 참고).

    @Test
    @DisplayName("상세는 전 구성원이 조회 가능하고 토큰의 companyId를 쓴다")
    void detailIsAccessibleByAnyMemberAndUsesCompanyIdFromToken() throws Exception {
        authenticateAs(1L, COMPANY, TEAM, "MEMBER");
        when(getTeamActionDetailUseCase.getTeamActionDetail(eq(COMPANY), eq(10L)))
                .thenReturn(new TeamActionDetail(teamAction(), "GOODS", "개발팀", null, null, null, null, List.of()));

        mockMvc.perform(get("/api/team/actions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectTag").value("GOODS"))
                .andExpect(jsonPath("$.data.teamName").value("개발팀"));
    }

    @Test
    @DisplayName("?tab=timeline이면 하위 개인 액션 타임라인을 내려준다")
    void timelineIsRoutedByTabQueryParamAndUsesCompanyIdFromToken() throws Exception {
        authenticateAs(1L, COMPANY, TEAM, "MEMBER");
        when(getTeamActionTimelineUseCase.getTeamActionTimeline(eq(COMPANY), eq(10L)))
                .thenReturn(List.of(new TimelineItem(personalAction(), "이태연")));

        mockMvc.perform(get("/api/team/actions/10").param("tab", "timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assigneeName").value("이태연"));
    }

    @Test
    @DisplayName("첨부파일 다운로드 URL 발급은 전 구성원 접근 가능하고 토큰의 companyId를 쓴다")
    void issueAttachmentDownloadUrlIsAccessibleByAnyMemberAndUsesCompanyIdFromToken() throws Exception {
        authenticateAs(1L, COMPANY, TEAM, "MEMBER");
        when(issueTeamActionAttachmentDownloadUrlUseCase.issueAttachmentDownloadUrl(eq(COMPANY), eq(10L), eq(1L)))
                .thenReturn(new IssuedDownloadUrl("https://s3/get", 300));

        mockMvc.perform(get("/api/team/actions/10/attachments/1/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/get"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    }

    @Test
    @DisplayName("팀 대시보드 요약은 토큰의 teamId·memberId로 조회한다 — 이슈 #352")
    void getTeamDashboardSummaryTakesTeamAndMemberFromToken() throws Exception {
        authenticateAs(55L, COMPANY, TEAM, "LEADER");
        when(getTeamDashboardSummaryUseCase.getTeamDashboardSummary(TEAM, 55L))
                .thenReturn(new TeamDashboardSummary(3L, 6L, 1L, 0L));

        mockMvc.perform(get("/api/team/actions/dashboard-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamActionCount").value(3))
                .andExpect(jsonPath("$.data.teamMemberActionCount").value(6))
                .andExpect(jsonPath("$.data.myActionCount").value(1))
                .andExpect(jsonPath("$.data.completedActionCount").value(0));
    }

    private void authenticateAs(Long memberId, Long companyId, Long teamId, String authority) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, authority, false, teamId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + authority))));
    }

    private Action teamAction() {
        return Action.reconstitute(
                10L, COMPANY, 100L, null, null, TEAM, null,
                ActionType.TEAM, "팀 액션", "설명", false, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
    }

    private Action personalAction() {
        return Action.reconstitute(
                11L, COMPANY, 100L, 10L, null, null, 5L,
                ActionType.PERSONAL, "개인 액션", "설명", false, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
    }
}
