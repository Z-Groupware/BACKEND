package com.module06.backend.calendar.presentation.api;

import java.time.LocalDate;
import java.time.YearMonth;
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

import com.module06.backend.calendar.application.usecase.CalendarItem;
import com.module06.backend.calendar.application.usecase.CalendarItemType;
import com.module06.backend.calendar.application.usecase.GetCalendarUseCase;
import com.module06.backend.global.security.AuthPrincipal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CalendarController")
@WebMvcTest(CalendarController.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarControllerTest {

    private static final Long COMPANY = 1L;
    private static final Long MEMBER = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCalendarUseCase getCalendarUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    @Test
    @DisplayName("month 쿼리파라미터를 그대로 유스케이스에 넘긴다")
    void passesRequestedMonthToUseCase() throws Exception {
        authenticateAs(MEMBER, COMPANY, "OWNER");
        when(getCalendarUseCase.getCalendar(eq(COMPANY), eq(MEMBER), eq("OWNER"), eq(YearMonth.of(2026, 8))))
                .thenReturn(List.of(new CalendarItem(
                        CalendarItemType.PROJECT, null, "프로젝트A", "TAG",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), null)));

        mockMvc.perform(get("/api/calendar").param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("PROJECT"))
                .andExpect(jsonPath("$.data[0].title").value("프로젝트A"))
                .andExpect(jsonPath("$.data[0].tag").value("TAG"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].isDone").doesNotExist());
    }

    @Test
    @DisplayName("TODO 항목은 id·isDone을 응답에 그대로 실어 보낸다")
    void includesIdAndIsDoneForTodoItems() throws Exception {
        authenticateAs(MEMBER, COMPANY, "MEMBER");
        when(getCalendarUseCase.getCalendar(eq(COMPANY), eq(MEMBER), eq("MEMBER"), eq(YearMonth.of(2026, 8))))
                .thenReturn(List.of(new CalendarItem(
                        CalendarItemType.TODO, 10L, "우유 사기", null,
                        LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), false)));

        mockMvc.perform(get("/api/calendar").param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TODO"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].isDone").value(false));
    }

    @Test
    @DisplayName("month 생략 시 이번 달로 조회한다")
    void defaultsToCurrentMonthWhenOmitted() throws Exception {
        authenticateAs(MEMBER, COMPANY, "MEMBER");
        when(getCalendarUseCase.getCalendar(eq(COMPANY), eq(MEMBER), eq("MEMBER"), eq(YearMonth.now())))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    private void authenticateAs(Long memberId, Long companyId, String authority) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, authority, false, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + authority))));
    }
}
