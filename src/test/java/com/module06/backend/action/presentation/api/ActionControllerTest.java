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

import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.usecase.BulkUpdateActionStatusUseCase;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.global.security.AuthPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private void authenticateAs(Long companyId, Long memberId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, "MEMBER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Action action() {
        return Action.createManual(1L, 100L, null, 5L, ActionType.PERSONAL, "수동 추가", "설명",
                LocalDate.of(2026, 12, 31));
    }
}
