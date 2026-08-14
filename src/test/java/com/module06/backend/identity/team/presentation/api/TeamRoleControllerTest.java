package com.module06.backend.identity.team.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.module06.backend.identity.team.application.command.CreateTeamRoleCommand;
import com.module06.backend.identity.team.application.command.RenameTeamRoleCommand;
import com.module06.backend.identity.team.application.dto.RoleNode;
import com.module06.backend.identity.team.application.usecase.CreateTeamRoleUseCase;
import com.module06.backend.identity.team.application.usecase.DeleteTeamRoleUseCase;
import com.module06.backend.identity.team.application.usecase.RenameTeamRoleUseCase;

/*
 * TeamControllerTest 와 같은 방식 — companyId 가 토큰에서만 오는지를 고정한다.
 * @PreAuthorize 롤 차단은 이 슬라이스에서 평가되지 않는다(@EnableMethodSecurity 미로드).
 */
@DisplayName("TeamRoleController")
@WebMvcTest(TeamRoleController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTeamRoleUseCase createTeamRoleUseCase;
    @MockitoBean
    private RenameTeamRoleUseCase renameTeamRoleUseCase;
    @MockitoBean
    private DeleteTeamRoleUseCase deleteTeamRoleUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("생성은 토큰의 회사와 경로의 팀 id로 역할을 만든다")
    void createTakesCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(createTeamRoleUseCase.create(any())).thenReturn(new RoleNode(101L, "백엔드", 0L));

        mockMvc.perform(post("/api/teams/10/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "백엔드" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roleId").value(101))
                .andExpect(jsonPath("$.data.name").value("백엔드"))
                /* 방금 만든 역할이라 아무도 달고 있지 않다. */
                .andExpect(jsonPath("$.data.memberCount").value(0));

        ArgumentCaptor<CreateTeamRoleCommand> captor = ArgumentCaptor.forClass(CreateTeamRoleCommand.class);
        verify(createTeamRoleUseCase).create(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().teamId()).isEqualTo(10L);
        assertThat(captor.getValue().name()).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("헤더에 남의 회사 번호를 넣어도 토큰 값이 쓰인다")
    void createIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L);
        when(createTeamRoleUseCase.create(any())).thenReturn(new RoleNode(101L, "백엔드", 0L));

        mockMvc.perform(post("/api/teams/10/roles")
                        .header("X-Company-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "백엔드" }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateTeamRoleCommand> captor = ArgumentCaptor.forClass(CreateTeamRoleCommand.class);
        verify(createTeamRoleUseCase).create(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("역할명이 비어 있으면 400으로 거절한다")
    void createRejectsBlankName() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/teams/10/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "   " }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createTeamRoleUseCase);
    }

    @Test
    @DisplayName("역할명이 50자를 넘으면 400으로 거절한다 — role.name 이 VARCHAR(50) 이다")
    void createRejectsNameLongerThanFiftyChars() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/teams/10/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"%s\" }".formatted("가".repeat(51))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createTeamRoleUseCase);
    }

    @Test
    @DisplayName("이름 수정은 경로의 팀·역할 id와 토큰의 회사로 요청한다")
    void renameTakesIdsFromPathAndCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(renameTeamRoleUseCase.rename(any())).thenReturn(new RoleNode(101L, "서버", 3L));

        mockMvc.perform(patch("/api/teams/10/roles/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "서버" }
                                """))
                .andExpect(status().isOk())
                /* 이름만 바뀌고 배정된 사람은 그대로다 — 응답의 인원 수도 실데이터다. */
                .andExpect(jsonPath("$.data.memberCount").value(3));

        ArgumentCaptor<RenameTeamRoleCommand> captor = ArgumentCaptor.forClass(RenameTeamRoleCommand.class);
        verify(renameTeamRoleUseCase).rename(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().teamId()).isEqualTo(10L);
        assertThat(captor.getValue().roleId()).isEqualTo(101L);
        assertThat(captor.getValue().name()).isEqualTo("서버");
    }

    @Test
    @DisplayName("삭제는 경로의 팀·역할 id와 토큰의 회사로 요청한다")
    void deleteTakesIdsFromPathAndCompanyFromToken() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete("/api/teams/10/roles/101"))
                .andExpect(status().isOk());

        verify(deleteTeamRoleUseCase).delete(1L, 10L, 101L);
    }

    private void authenticateAs(Long companyId) {
        AuthPrincipal principal = new AuthPrincipal(3L, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
