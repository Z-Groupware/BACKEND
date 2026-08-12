package com.module06.backend.action.presentation.api;

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

import com.module06.backend.action.application.command.BulkUpdateActionStatusCommand;
import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.usecase.BulkUpdateActionStatusUseCase;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase.ActionDetail;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase.ActionListItem;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.global.security.AuthPrincipal;

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

@DisplayName("ActionController")
@WebMvcTest(ActionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActionControllerTest {

    private static final String BODY = """
            {
              "projectId": 100,
              "actionType": "PERSONAL",
              "assigneeMemberId": 5,
              "title": "수동 추가",
              "description": "설명",
              "dueDate": "2026-12-31"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateActionUseCase createActionUseCase;

    // ActionController가 생성자로 함께 받는 나머지 UseCase — @WebMvcTest는 등록 안 된 빈이
    // 하나라도 있으면 컨텍스트 로딩 자체가 실패한다.
    @MockitoBean
    private GetMyActionsUseCase getMyActionsUseCase;

    @MockitoBean
    private GetActionDetailUseCase getActionDetailUseCase;

    @MockitoBean
    private BulkUpdateActionStatusUseCase bulkUpdateActionStatusUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("수동 추가는 토큰의 companyId를 사용한다")
    void createTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(createActionUseCase.create(any())).thenReturn(action());

        mockMvc.perform(post("/api/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateActionCommand> captor = ArgumentCaptor.forClass(CreateActionCommand.class);
        verify(createActionUseCase).create(captor.capture());

        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().projectId()).isEqualTo(100L);
        assertThat(captor.getValue().assigneeMemberId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("헤더에 남의 회사 번호를 넣어도 토큰 값이 쓰인다")
    void createIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L, 3L);
        when(createActionUseCase.create(any())).thenReturn(action());

        mockMvc.perform(post("/api/actions")
                        .header("X-Company-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateActionCommand> captor = ArgumentCaptor.forClass(CreateActionCommand.class);
        verify(createActionUseCase).create(captor.capture());

        assertThat(captor.getValue().companyId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("내 액션 목록은 토큰의 memberId로 조회한다")
    void listUsesMemberIdFromToken() throws Exception {
        // 비기본 page/size(2, 5)로 요청해서, PageResponse 필드 6개(content·page·size·
        // totalElements·totalPages·hasNext) 전부가 요청값 기준으로 정확히 계산되는지 확인한다
        // (CodeRabbit 지적, PR #305) — totalElements=42, size=5면 totalPages=9, hasNext=true.
        authenticateAs(1L, 5L);
        when(getMyActionsUseCase.getMyActions(eq(5L), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetMyActionsUseCase.ActionListResult(
                        List.of(new ActionListItem(action(), "이하윤", "GOODS", "굿즈", "개발팀", "기획 회의", null)), 42L));

        mockMvc.perform(get("/api/actions").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].assigneeName").value("이하윤"))
                .andExpect(jsonPath("$.data.content[0].projectName").value("굿즈"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(42))
                .andExpect(jsonPath("$.data.totalPages").value(9))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(getMyActionsUseCase).getMyActions(5L, "MEMBER", null, null, null, null, null, "desc", 2, 5);
    }

    // CodeRabbit 지적(2026-08-11) — assigneeMemberId를 실제로 전달하는 위임 조회 경로가
    // 컨트롤러 계약 테스트에 없었다. LEADER principal(teamId 포함)로 요청해 유스케이스가
    // 요청자 id·authority·teamId·대상 id를 그대로 넘기는지 확인한다.
    @Test
    @DisplayName("assigneeMemberId를 주면 LEADER의 요청자 정보·팀 id와 함께 위임 조회한다")
    void listDelegatesToTargetMemberWhenAssigneeMemberIdGiven() throws Exception {
        authenticateAsLeader(1L, 5L, 7L);
        when(getMyActionsUseCase.getMyActions(eq(5L), eq("LEADER"), eq(7L), eq(9L), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetMyActionsUseCase.ActionListResult(
                        List.of(new ActionListItem(action(), "박도현", "GOODS", "굿즈", "개발팀", "기획 회의", null)), 1L));

        mockMvc.perform(get("/api/actions").param("assigneeMemberId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].assigneeName").value("박도현"));

        verify(getMyActionsUseCase).getMyActions(5L, "LEADER", 7L, 9L, null, null, null, "desc", 0, 20);
    }

    @Test
    @DisplayName("상세 조회는 토큰의 companyId로 IDOR을 막는다")
    void detailUsesCompanyIdFromToken() throws Exception {
        authenticateAs(1L, 5L);
        when(getActionDetailUseCase.getActionDetail(eq(1L), eq(10L)))
                .thenReturn(new ActionDetail(action(), "이하윤", null, "GOODS", "굿즈", "개발팀", "기획 회의", null, null, null, null));

        mockMvc.perform(get("/api/actions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeName").value("이하윤"));

        verify(getActionDetailUseCase).getActionDetail(1L, 10L);
    }

    @Test
    @DisplayName("startDate는 목록·상세 응답 둘 다 노출된다 (2026-08-10, 이홍근 요청)")
    void exposesStartDateOnListAndDetail() throws Exception {
        authenticateAs(1L, 5L);
        Action inProgress = Action.reconstitute(
                10L, 1L, 100L, null, null, null, 5L, ActionType.PERSONAL, "진행중 액션", "설명",
                false, LocalDate.of(2026, 8, 5), null, LocalDate.of(2026, 12, 31), false,
                com.module06.backend.action.domain.model.ActionReviewStatus.HUMAN_CONFIRMED,
                null, null, null, true, null, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());

        when(getMyActionsUseCase.getMyActions(eq(5L), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new GetMyActionsUseCase.ActionListResult(
                        List.of(new ActionListItem(inProgress, "이하윤", "GOODS", "굿즈", "개발팀", "기획 회의", null)), 1L));
        when(getActionDetailUseCase.getActionDetail(eq(1L), eq(10L)))
                .thenReturn(new ActionDetail(inProgress, "이하윤", null, "GOODS", "굿즈", "개발팀", "기획 회의", null, null, null, null));

        mockMvc.perform(get("/api/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].startDate").value("2026-08-05"));

        mockMvc.perform(get("/api/actions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-08-05"));
    }

    @Test
    @DisplayName("startDate는 아직 시작 안 한(TODO) 액션이면 null로 내려간다")
    void startDateIsNullForNotYetStartedAction() throws Exception {
        authenticateAs(1L, 5L);

        when(getActionDetailUseCase.getActionDetail(eq(1L), eq(10L)))
                .thenReturn(new ActionDetail(action(), "이하윤", null, "GOODS", "굿즈", "개발팀", "기획 회의", null, null, null, null));

        mockMvc.perform(get("/api/actions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("벌크 상태변경은 토큰의 memberId를 requesterId로, items를 그대로 커맨드로 전달한다")
    void bulkUpdateStatusConvertsItemsAndUsesMemberIdFromToken() throws Exception {
        authenticateAs(1L, 5L);

        mockMvc.perform(patch("/api/actions/complete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"actionId": 10, "status": "DONE"}, {"actionId": 11, "status": "IN_PROGRESS"}]}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<BulkUpdateActionStatusCommand> captor = ArgumentCaptor.forClass(BulkUpdateActionStatusCommand.class);
        verify(bulkUpdateActionStatusUseCase).bulkUpdateStatus(captor.capture());

        BulkUpdateActionStatusCommand command = captor.getValue();
        assertThat(command.requesterId()).isEqualTo(5L);
        assertThat(command.items()).containsExactly(
                new BulkUpdateActionStatusCommand.Item(10L, ActionStatus.DONE),
                new BulkUpdateActionStatusCommand.Item(11L, ActionStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("벌크 상태변경은 빈 items를 400으로 거부한다")
    void bulkUpdateStatusRejectsEmptyItems() throws Exception {
        authenticateAs(1L, 5L);

        mockMvc.perform(patch("/api/actions/complete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void authenticateAs(Long companyId, Long memberId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, "MEMBER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private void authenticateAsLeader(Long companyId, Long memberId, Long teamId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, "LEADER", false, teamId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Action action() {
        return Action.createManual(1L, 100L, null, 5L, ActionType.PERSONAL, "수동 추가", "설명",
                LocalDate.of(2026, 12, 31));
    }
}
