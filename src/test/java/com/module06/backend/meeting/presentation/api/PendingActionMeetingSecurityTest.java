package com.module06.backend.meeting.presentation.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.meeting.application.usecase.GetPendingActionMeetingsUseCase;

/* MEET-10 익명 요청이 AuthPrincipal 인자 해석 전에 공통 보안 필터에서 차단되는지 검증한다. */
@DisplayName("MEET-10 확정 대기 회의 조회 보안")
@SpringBootTest
@AutoConfigureMockMvc
class PendingActionMeetingSecurityTest {

    /* 실제 SecurityFilterChain과 MVC 인자 해석을 통과하는 HTTP 요청 실행기다. */
    @Autowired
    private MockMvc mockMvc;

    /* 인증 실패 요청이 확정 대기 조회 비즈니스 로직에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private GetPendingActionMeetingsUseCase getPendingActionMeetingsUseCase;

    /* Access Token 없는 GET 요청이 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 GET 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousPendingActionMeetingRequest() throws Exception {
        /* 인증 헤더 없이 확정 대기 회의 목록 조회를 전송한다. */
        mockMvc.perform(get("/api/v1/meetings/pending-action-distributions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 조회 유스케이스를 한 번도 호출하면 안 된다. */
        verifyNoInteractions(getPendingActionMeetingsUseCase);
    }
}
