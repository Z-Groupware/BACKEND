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

import com.module06.backend.action.application.port.ActionQueryPort.TeamActionSummary;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.project.application.command.BulkUpdateProjectStatusCommand;
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.command.UpdateProjectCommand;
import com.module06.backend.project.application.usecase.BulkUpdateProjectStatusUseCase;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase.ProjectDetailResult;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase.ProjectListItem;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase.TimelineItem;
import com.module06.backend.project.application.usecase.UpdateProjectUseCase;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.exception.ProjectErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @MockitoBean
    private GetProjectDetailUseCase getProjectDetailUseCase;

    @MockitoBean
    private UpdateProjectUseCase updateProjectUseCase;

    @MockitoBean
    private BulkUpdateProjectStatusUseCase bulkUpdateProjectStatusUseCase;

    @MockitoBean
    private GetProjectTimelineUseCase getProjectTimelineUseCase;

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
        when(getProjectListUseCase.list(any())).thenReturn(List.of(
                new GetProjectListUseCase.ProjectListItem(project(1L), 0, 0, 0, List.of())));

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

    @Test
    @DisplayName("상세조회는 토큰의 회사로 프로젝트를 조회한다")
    void getDetailTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectDetailUseCase.getDetail(1L, 100L))
                .thenReturn(new ProjectDetailResult(project(1L), List.of()));

        mockMvc.perform(get("/api/projects/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새 프로젝트"));

        verify(getProjectDetailUseCase).getDetail(1L, 100L);
    }

    @Test
    @DisplayName("존재하지 않거나 다른 회사 프로젝트를 상세조회하면 예외가 전파된다")
    void getDetailPropagatesNotFound() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectDetailUseCase.getDetail(1L, 100L))
                .thenThrow(new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(get("/api/projects/100"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("수정은 토큰의 memberId를 requesterId로 사용한다")
    void updateTakesRequesterFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(updateProjectUseCase.update(any())).thenReturn(project(1L));

        mockMvc.perform(patch("/api/projects/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정된 이름",
                                  "description": "수정된 설명",
                                  "color": "#000000",
                                  "dueDate": "2027-01-01",
                                  "teamIds": [5]
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProjectCommand> captor = ArgumentCaptor.forClass(UpdateProjectCommand.class);
        verify(updateProjectUseCase).update(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(100L);
        assertThat(captor.getValue().requesterId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("벌크 상태변경은 토큰의 memberId를 requesterId로 사용한다")
    void bulkUpdateStatusTakesRequesterFromToken() throws Exception {
        authenticateAs(1L, 3L);

        mockMvc.perform(patch("/api/projects/status/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "projectId": 1, "status": "DONE" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<BulkUpdateProjectStatusCommand> captor = ArgumentCaptor.forClass(BulkUpdateProjectStatusCommand.class);
        verify(bulkUpdateProjectStatusUseCase).bulkUpdateStatus(captor.capture());
        assertThat(captor.getValue().requesterId()).isEqualTo(3L);
        assertThat(captor.getValue().items()).containsExactly(
                new BulkUpdateProjectStatusCommand.Item(1L, ProjectStatus.DONE));
    }

    @Test
    @DisplayName("타임라인은 토큰의 회사로 조회하고 지연 여부를 그대로 내려준다")
    void getTimelineTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectTimelineUseCase.getTimeline(1L, 100L)).thenReturn(List.of(
                new TimelineItem(10L, "팀 액션", 1L, "개발팀", ActionStatus.IN_PROGRESS, LocalDate.of(2020, 1, 1), true)
        ));

        mockMvc.perform(get("/api/projects/100/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actionId").value(10))
                .andExpect(jsonPath("$.data[0].isDelayed").value(true));

        verify(getProjectTimelineUseCase).getTimeline(1L, 100L);
    }

    /*
     * 익명 요청은 여기서 검증하지 않는다. 이유가 두 겹이다.
     *
     * 1) 이 슬라이스로는 볼 수 없다 — @WebMvcTest 는 @Configuration 을 스캔하지 않아
     *    SecurityConfig 의 @EnableMethodSecurity 가 없고, 그러면 @PreAuthorize 가 평가되지 않는다.
     *
     * 2) 실제 서버에서도 지금은 401 이 아니라 500 이 난다. 익명 요청의 principal 은 AuthPrincipal 이
     *    아니라 문자열 "anonymousUser" 라서, @AuthenticationPrincipal(expression = "companyId") 가
     *    SpEL 평가에서 터진다(EL1008E). 인자 해석이 @PreAuthorize 보다 먼저라 401 로 갈 기회가 없다.
     *    이건 이 변경이 만든 문제가 아니다 — develop 의 회의·회의실 API 도 같은 패턴이라 똑같이
     *    500 이다(2026-08-05 실제 서버 확인). Task 10 이 anyRequest().authenticated() 로 뒤집으면
     *    필터가 인자 해석 전에 막아서 세 도메인이 한꺼번에 해소된다.
     */

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
