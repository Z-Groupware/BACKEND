package com.module06.backend.action.presentation.api;

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

import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase;
import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase.TeamMemberItem;
import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase.TeamMemberStatusList;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ReferenceMemberStatus;
import com.module06.backend.global.security.AuthPrincipal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TeamMemberController")
@WebMvcTest(TeamMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamMemberControllerTest {

    private static final Long TEAM = 7L;
    private static final Long COMPANY = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTeamMemberStatusUseCase getTeamMemberStatusUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("팀원 현황은 토큰의 teamId로 조회한다 — 이슈 #352")
    void listUsesTeamIdFromToken() throws Exception {
        authenticateAs(55L, COMPANY, TEAM, "LEADER");
        when(getTeamMemberStatusUseCase.getTeamMemberStatus(TEAM)).thenReturn(new TeamMemberStatusList(List.of(
                new TeamMemberItem(10L, "이하윤", "선임", "프론트엔드", ReferenceMemberStatus.ACTIVE, 3L)
        )));

        mockMvc.perform(get("/api/team/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("이하윤"))
                .andExpect(jsonPath("$.data[0].positionName").value("선임"))
                .andExpect(jsonPath("$.data[0].roleName").value("프론트엔드"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].actionCount").value(3));
    }

    @Test
    @DisplayName("팀원이 없으면 빈 배열을 반환한다")
    void listReturnsEmptyArrayWhenTeamHasNoMembers() throws Exception {
        authenticateAs(55L, COMPANY, TEAM, "LEADER");
        when(getTeamMemberStatusUseCase.getTeamMemberStatus(TEAM)).thenReturn(new TeamMemberStatusList(List.of()));

        mockMvc.perform(get("/api/team/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private void authenticateAs(Long memberId, Long companyId, Long teamId, String authority) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, authority, false, teamId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + authority))));
    }
}
