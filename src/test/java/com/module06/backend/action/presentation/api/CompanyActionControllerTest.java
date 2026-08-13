package com.module06.backend.action.presentation.api;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.action.application.usecase.GetCompanyMemberActionsUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase.ActionListItem;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase.ActionListResult;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.global.security.AuthPrincipal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 배선(companyId 토큰 추출, assigneeMemberId 필수, 파라미터 전달) 검증. @PreAuthorize
 * 권한 경계는 addFilters=false 슬라이스로는 안 보인다(TeamActionControllerTest와 동일 이유,
 * CompanyActionControllerSecurityTest에서 실 필터체인으로 별도 검증).
 */
@DisplayName("CompanyActionController")
@WebMvcTest(CompanyActionController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompanyActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCompanyMemberActionsUseCase getCompanyMemberActionsUseCase;

    @Test
    @DisplayName("companyId는 토큰에서만 꺼내고, assigneeMemberId·필터를 그대로 전달한다")
    void listTakesCompanyFromTokenAndPassesParamsThrough() throws Exception {
        authenticateAs(1L, "OWNER");
        Action action = Action.createManual(1L, 100L, null, 9L, ActionType.PERSONAL, "제목", "설명",
                LocalDate.of(2026, 12, 31));
        when(getCompanyMemberActionsUseCase.getCompanyMemberActions(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ActionListResult(List.of(new ActionListItem(action, "박종준", "GOODS", "굿즈", "개발팀", null, null)), 1L));

        mockMvc.perform(get("/api/company/actions")
                        .param("assigneeMemberId", "9")
                        .param("sort", "dueDate")
                        .param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].assigneeName").value("박종준"));

        verify(getCompanyMemberActionsUseCase).getCompanyMemberActions(
                eq(1L), eq(9L), eq(null), eq(null), eq("dueDate"), eq("asc"), eq(0), eq(20));
    }

    @Test
    @DisplayName("헤더로 회사를 조작해도 토큰의 companyId만 쓴다")
    void listIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L, "OWNER");
        when(getCompanyMemberActionsUseCase.getCompanyMemberActions(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ActionListResult(List.of(), 0L));

        mockMvc.perform(get("/api/company/actions")
                        .param("assigneeMemberId", "9")
                        .header("X-Company-Id", "999"))
                .andExpect(status().isOk());

        verify(getCompanyMemberActionsUseCase).getCompanyMemberActions(
                eq(1L), eq(9L), eq(null), eq(null), eq(null), eq("desc"), eq(0), eq(20));
    }

    @Test
    @DisplayName("assigneeMemberId 없이도 컨트롤러는 호출을 서비스로 그대로 넘긴다(필수 검증은 ActionService 책임)")
    void listPassesNullAssigneeMemberIdThrough() throws Exception {
        authenticateAs(1L, "OWNER");
        when(getCompanyMemberActionsUseCase.getCompanyMemberActions(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ActionListResult(List.of(), 0L));

        mockMvc.perform(get("/api/company/actions"))
                .andExpect(status().isOk());

        verify(getCompanyMemberActionsUseCase).getCompanyMemberActions(
                eq(1L), eq(null), eq(null), eq(null), eq(null), eq("desc"), eq(0), eq(20));
    }

    private void authenticateAs(Long companyId, String authority) {
        AuthPrincipal principal = new AuthPrincipal(3L, companyId, authority, false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
