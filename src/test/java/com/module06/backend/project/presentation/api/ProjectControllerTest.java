package com.module06.backend.project.presentation.api;

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
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.domain.model.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 회사 스코프가 어디서 오는지를 고정하는 테스트다.
 *
 * 헤더로 받으면 로그인만 하면 남의 회사 번호를 적어 보낼 수 있다 — 인증을 걸어도 막히지 않는
 * 구멍이라, 토큰에서만 꺼내야 한다. 아래 테스트들은 "헤더가 있어도 무시되는지"까지 확인한다.
 *
 * 생성이 201 이 아니라 200 을 기대하는 것은 의도다. ApiResponse.created() 가 본문의 httpStatus 를
 * 201 로 채우지만 메서드에 @ResponseStatus 가 없어서 실제 HTTP 상태는 200 이다(HandoverController 는
 * 붙여 뒀다). 이 변경의 범위는 스코프 출처를 바꾸는 것이라, 프론트 계약이 바뀌는 그 정정은
 * project 담당자에게 남긴다 — 여기서는 현재 동작을 그대로 고정한다.
 */
@DisplayName("ProjectController")
@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    private static final String BODY = """
            {
              "name": "새 프로젝트",
              "tag": "NEWPJ",
              "description": "설명",
              "color": "#16A34A",
              "dueDate": "2026-12-31",
              "teamIds": [1, 2]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateProjectUseCase createProjectUseCase;

    @MockitoBean
    private GetProjectListUseCase getProjectListUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("생성은 헤더 없이 토큰만으로 동작한다 — 회사·작성자를 토큰에서 꺼낸다")
    void createTakesScopeFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(createProjectUseCase.create(any())).thenReturn(project(1L));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateProjectCommand> captor = ArgumentCaptor.forClass(CreateProjectCommand.class);
        verify(createProjectUseCase).create(captor.capture());

        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().createdBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("헤더에 남의 회사 번호를 넣어도 토큰 값이 쓰인다 — 이게 막으려던 구멍이다")
    void createIgnoresSpoofedHeaders() throws Exception {
        authenticateAs(1L, 3L);
        when(createProjectUseCase.create(any())).thenReturn(project(1L));

        mockMvc.perform(post("/api/projects")
                        .header("X-Company-Id", "999")
                        .header("X-Member-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateProjectCommand> captor = ArgumentCaptor.forClass(CreateProjectCommand.class);
        verify(createProjectUseCase).create(captor.capture());

        assertThat(captor.getValue().companyId())
                .as("헤더의 999 가 아니라 토큰의 1 이어야 한다")
                .isEqualTo(1L);
        assertThat(captor.getValue().createdBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("목록도 토큰의 회사로만 조회한다")
    void listTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any())).thenReturn(List.of(project(1L)));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk());

        verify(getProjectListUseCase).list(1L);
    }

    @Test
    @DisplayName("목록 조회도 헤더를 무시한다")
    void listIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/projects").header("X-Company-Id", "999"))
                .andExpect(status().isOk());

        verify(getProjectListUseCase).list(eq(1L));
    }

    /** 필터를 끈 슬라이스 테스트라 컨텍스트를 직접 심는다 — AuthControllerTest 와 같은 방식. */
    private void authenticateAs(Long companyId, Long memberId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Project project(Long companyId) {
        return Project.create(companyId, "NEWPJ", "새 프로젝트", "설명", "#16A34A",
                LocalDate.of(2026, 12, 31), 3L, List.of(1L, 2L));
    }
}
