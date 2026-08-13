package com.module06.backend.action.presentation.api;

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

import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase;
import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase.MeetingActionItem;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.global.security.AuthPrincipal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MeetingActionController")
@WebMvcTest(MeetingActionController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeetingActionControllerTest {

    private static final Long COMPANY = 1L;
    private static final Long MEETING = 200L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetActionsByMeetingUseCase getActionsByMeetingUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("전 구성원이 조회 가능하고 토큰의 companyId·경로의 meetingId를 그대로 전달한다")
    void listByMeetingUsesCompanyIdFromTokenAndMeetingIdFromPath() throws Exception {
        authenticateAs(1L, COMPANY, "MEMBER");
        when(getActionsByMeetingUseCase.getActionsByMeeting(eq(COMPANY), eq(MEETING)))
                .thenReturn(List.of(
                        new MeetingActionItem(teamAction(), null, "개발팀"),
                        new MeetingActionItem(personalAction(), "이하윤", null)
                ));

        mockMvc.perform(get("/api/meetings/{meetingId}/actions", MEETING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamName").value("개발팀"))
                .andExpect(jsonPath("$.data[0].assigneeName").value(nullValue()))
                .andExpect(jsonPath("$.data[1].assigneeName").value("이하윤"))
                .andExpect(jsonPath("$.data[1].teamName").value(nullValue()));
    }

    private void authenticateAs(Long memberId, Long companyId, String authority) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, authority, false, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + authority))));
    }

    private Action teamAction() {
        return Action.reconstitute(
                10L, COMPANY, 100L, null, MEETING, 7L, null,
                ActionType.TEAM, "팀 액션", "설명", false, null, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
    }

    private Action personalAction() {
        return Action.reconstitute(
                11L, COMPANY, 100L, 10L, MEETING, null, 5L,
                ActionType.PERSONAL, "개인 액션", "설명", false, LocalDate.of(2026, 8, 1), null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
    }
}
