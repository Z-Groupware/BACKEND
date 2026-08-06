package com.module06.backend.identity.team.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.module06.backend.identity.team.application.command.CreateTeamCommand;
import com.module06.backend.identity.team.application.command.RenameTeamCommand;
import com.module06.backend.identity.team.application.dto.TeamNode;
import com.module06.backend.identity.team.application.usecase.CreateTeamUseCase;
import com.module06.backend.identity.team.application.usecase.DeleteTeamUseCase;
import com.module06.backend.identity.team.application.usecase.GetTeamTreeUseCase;
import com.module06.backend.identity.team.application.usecase.RenameTeamUseCase;

import java.util.List;

/*
 * ProjectControllerTest 와 같은 방식 — companyId 가 토큰에서만 오는지를 고정한다.
 * @PreAuthorize 롤 차단은 이 슬라이스에서 평가되지 않는다(@EnableMethodSecurity 미로드,
 * ProjectControllerTest 주석 참조) — 그건 TeamServiceTest 가 아니라 실제 서버 동작이며,
 * 이 계획의 범위에서는 별도 통합 테스트를 추가하지 않는다(기존 컨벤션과 동일한 수준).
 */
@DisplayName("TeamController")
@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTeamTreeUseCase getTeamTreeUseCase;
    @MockitoBean
    private CreateTeamUseCase createTeamUseCase;
    @MockitoBean
    private RenameTeamUseCase renameTeamUseCase;
    @MockitoBean
    private DeleteTeamUseCase deleteTeamUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("트리 조회는 토큰의 회사로만 조회한다")
    void treeTakesCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(getTeamTreeUseCase.getTree(1L)).thenReturn(List.of(
                new TeamNode(10L, "본부", null, null, null, 0L, List.of())));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk());

        verify(getTeamTreeUseCase).getTree(1L);
    }

    @Test
    @DisplayName("헤더에 남의 회사 번호를 넣어도 토큰 값이 쓰인다")
    void treeIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L);
        when(getTeamTreeUseCase.getTree(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/teams").header("X-Company-Id", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(getTeamTreeUseCase).getTree(captor.capture());
        assertThat(captor.getValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("생성은 토큰의 회사로 부서를 만든다")
    void createTakesCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(createTeamUseCase.create(any())).thenReturn(
                new TeamNode(10L, "사업본부", null, null, null, 0L, List.of()));

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "사업본부", "parentTeamId": null }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateTeamCommand> captor = ArgumentCaptor.forClass(CreateTeamCommand.class);
        verify(createTeamUseCase).create(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().name()).isEqualTo("사업본부");
    }

    @Test
    @DisplayName("이름 수정은 경로의 팀 id와 토큰의 회사로 요청한다")
    void renameTakesTeamIdFromPathAndCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(renameTeamUseCase.rename(any())).thenReturn(
                new TeamNode(10L, "제품개발팀", null, null, null, 0L, List.of()));

        mockMvc.perform(patch("/api/teams/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "제품개발팀" }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<RenameTeamCommand> captor = ArgumentCaptor.forClass(RenameTeamCommand.class);
        verify(renameTeamUseCase).rename(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().teamId()).isEqualTo(10L);
        assertThat(captor.getValue().name()).isEqualTo("제품개발팀");
    }

    @Test
    @DisplayName("삭제는 경로의 팀 id와 토큰의 회사로 요청한다")
    void deleteTakesTeamIdFromPathAndCompanyFromToken() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete("/api/teams/10"))
                .andExpect(status().isOk());

        verify(deleteTeamUseCase).delete(1L, 10L);
    }

    private void authenticateAs(Long companyId) {
        AuthPrincipal principal = new AuthPrincipal(3L, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
