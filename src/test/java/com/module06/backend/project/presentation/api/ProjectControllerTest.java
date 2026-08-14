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
import com.module06.backend.project.application.usecase.GetOwnerDashboardSummaryUseCase;
import com.module06.backend.project.application.usecase.GetOwnerDashboardSummaryUseCase.OwnerDashboardSummary;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
 * 생성 응답은 201 이다(2026-08-10). 본문 httpStatus 는 ApiResponse.created() 가 이미 201 로
 * 채우고 있었는데 @ResponseStatus 가 빠져서 실제 HTTP 상태만 200 이던 불일치를 여기서 맞췄다
 * (HandoverController 등 다른 생성 엔드포인트와 동일하게). 프론트에는 사전 공지 후 반영.
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
              "color": "#059669",
              "startDate": "2026-08-01",
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

    @MockitoBean
    private GetOwnerDashboardSummaryUseCase getOwnerDashboardSummaryUseCase;

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
                .andExpect(status().isCreated());

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
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectCommand> captor = ArgumentCaptor.forClass(CreateProjectCommand.class);
        verify(createProjectUseCase).create(captor.capture());

        assertThat(captor.getValue().companyId())
                .as("헤더의 999 가 아니라 토큰의 1 이어야 한다")
                .isEqualTo(1L);
        assertThat(captor.getValue().createdBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("startDate는 dueDate처럼 생성 시 필수다 — 없으면 400 (2026-08-10, 이홍근 요청)")
    void createRejectsMissingStartDate() throws Exception {
        authenticateAs(1L, 3L);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 프로젝트",
                                  "tag": "NEWPJ",
                                  "description": "설명",
                                  "color": "#059669",
                                  "dueDate": "2026-12-31",
                                  "teamIds": [1, 2]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("색상은 고정 팔레트 11색만 허용한다 — 팔레트 밖 HEX는 생성 자체가 400으로 막힌다")
    void createRejectsColorOutsidePalette() throws Exception {
        authenticateAs(1L, 3L);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 프로젝트",
                                  "tag": "NEWPJ",
                                  "description": "설명",
                                  "color": "#123456",
                                  "startDate": "2026-08-01",
                                  "dueDate": "2026-12-31",
                                  "teamIds": [1, 2]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록도 토큰의 회사로만 조회한다")
    void listTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new GetProjectListUseCase.ProjectListResult(
                        List.of(new GetProjectListUseCase.ProjectListItem(project(1L), 0, 0, 0, List.of())), 1L));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].description").value("설명"));

        verify(getProjectListUseCase).list(1L, null, null, null, "desc", 0, 20);
    }

    @Test
    @DisplayName("목록 조회도 헤더를 무시한다")
    void listIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetProjectListUseCase.ProjectListResult(List.of(), 0L));

        mockMvc.perform(get("/api/projects").header("X-Company-Id", "999"))
                .andExpect(status().isOk());

        verify(getProjectListUseCase).list(eq(1L), eq(null), eq(null), eq(null), eq("desc"), eq(0), eq(20));
    }

    @Test
    @DisplayName("목록 조회는 page/size 쿼리파라미터를 그대로 UseCase에 전달한다")
    void listPassesPageAndSizeThrough() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetProjectListUseCase.ProjectListResult(List.of(), 0L));

        mockMvc.perform(get("/api/projects").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(getProjectListUseCase).list(1L, null, null, null, "desc", 2, 5);
    }

    @Test
    @DisplayName("목록 조회는 status·sort·order 쿼리파라미터도 그대로 전달한다 (2026-08-10, 이홍근 요청)")
    void listPassesFilterAndSortThrough() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetProjectListUseCase.ProjectListResult(List.of(), 0L));

        mockMvc.perform(get("/api/projects")
                        .param("status", "IN_PROGRESS")
                        .param("sort", "dueDate")
                        .param("order", "asc"))
                .andExpect(status().isOk());

        verify(getProjectListUseCase).list(1L, null, ProjectStatus.IN_PROGRESS, "dueDate", "asc", 0, 20);
    }

    @Test
    @DisplayName("목록 조회는 keyword 쿼리파라미터도 그대로 전달한다 (2026-08-13, 이홍근 요청)")
    void listPassesKeywordThrough() throws Exception {
        authenticateAs(1L, 3L);
        when(getProjectListUseCase.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetProjectListUseCase.ProjectListResult(List.of(), 0L));

        mockMvc.perform(get("/api/projects").param("keyword", "제브라"))
                .andExpect(status().isOk());

        verify(getProjectListUseCase).list(1L, "제브라", null, null, "desc", 0, 20);
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
                                  "color": "#4F46E5",
                                  "startDate": "2026-09-01",
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
    @DisplayName("벌크 상태변경 중 소유자가 아닌 항목이 있으면 예외가 전파된다")
    void bulkUpdateStatusPropagatesNotOwnerException() throws Exception {
        authenticateAs(1L, 3L);
        org.mockito.Mockito.doThrow(new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER))
                .when(bulkUpdateProjectStatusUseCase).bulkUpdateStatus(any());

        mockMvc.perform(patch("/api/projects/status/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "projectId": 1, "status": "DONE" }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
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

    @Test
    @DisplayName("오너 대시보드 요약은 토큰의 회사로 조회한다 — 이슈 #352")
    void getOwnerDashboardSummaryTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(getOwnerDashboardSummaryUseCase.getOwnerDashboardSummary(1L))
                .thenReturn(new OwnerDashboardSummary(3L, 1L));

        mockMvc.perform(get("/api/projects/dashboard-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProjectCount").value(3))
                .andExpect(jsonPath("$.data.dueSoonProjectCount").value(1));

        verify(getOwnerDashboardSummaryUseCase).getOwnerDashboardSummary(1L);
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
        return Project.create(companyId, "NEWPJ", "새 프로젝트", "설명", "#059669",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), 3L, List.of(1L, 2L));
    }
}
