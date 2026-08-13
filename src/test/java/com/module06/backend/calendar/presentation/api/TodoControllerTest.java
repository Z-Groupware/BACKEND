package com.module06.backend.calendar.presentation.api;

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

import com.module06.backend.calendar.application.command.CreateTodoCommand;
import com.module06.backend.calendar.application.usecase.CreateTodoUseCase;
import com.module06.backend.calendar.application.usecase.ToggleTodoCompleteUseCase;
import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.global.security.AuthPrincipal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TodoController")
@WebMvcTest(TodoController.class)
@AutoConfigureMockMvc(addFilters = false)
class TodoControllerTest {

    private static final Long COMPANY = 1L;
    private static final Long MEMBER = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTodoUseCase createTodoUseCase;

    @MockitoBean
    private ToggleTodoCompleteUseCase toggleTodoCompleteUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    @Test
    @DisplayName("생성은 토큰의 companyId·memberId로 본인 소유 Todo를 만든다")
    void createUsesCompanyIdAndMemberIdFromToken() throws Exception {
        authenticateAs(MEMBER, COMPANY, "MEMBER");
        when(createTodoUseCase.create(new CreateTodoCommand(COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), null)))
                .thenReturn(todo(1L, "우유 사기", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), false));

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"title": "우유 사기", "date": "2026-08-20"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("우유 사기"))
                .andExpect(jsonPath("$.data.isDone").value(false))
                .andExpect(jsonPath("$.httpStatus").value(201));
    }

    @Test
    @DisplayName("endDate를 같이 보내면 그대로 커맨드에 실려 위임된다")
    void createPassesEndDateThrough() throws Exception {
        authenticateAs(MEMBER, COMPANY, "MEMBER");
        when(createTodoUseCase.create(new CreateTodoCommand(
                COMPANY, MEMBER, "여행", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 25))))
                .thenReturn(todo(1L, "여행", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 25), false));

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"title": "여행", "date": "2026-08-20", "endDate": "2026-08-25"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-08-20"))
                .andExpect(jsonPath("$.data.endDate").value("2026-08-25"));
    }

    @Test
    @DisplayName("빈 제목은 400으로 거부된다")
    void createRejectsBlankTitle() throws Exception {
        authenticateAs(MEMBER, COMPANY, "MEMBER");

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"title": "", "date": "2026-08-20"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("완료토글은 토큰의 memberId로 위임하고 결과를 그대로 반환한다")
    void toggleCompleteDelegatesToUseCase() throws Exception {
        authenticateAs(MEMBER, COMPANY, "MEMBER");
        when(toggleTodoCompleteUseCase.toggleComplete(eq(COMPANY), eq(MEMBER), eq(1L)))
                .thenReturn(todo(1L, "우유 사기", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), true));

        mockMvc.perform(patch("/api/todos/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDone").value(true));
    }

    private void authenticateAs(Long memberId, Long companyId, String authority) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, authority, false, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + authority))));
    }

    private PersonalTodo todo(Long id, String title, LocalDate date, LocalDate endDate, boolean isDone) {
        return PersonalTodo.reconstitute(id, COMPANY, MEMBER, title, date, endDate, isDone, null, null);
    }
}
