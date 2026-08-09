package com.module06.backend.meeting.presentation.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.meeting.application.usecase.CompleteMeetingUseCase;

/* MEET-08 익명 요청이 AuthPrincipal 인자 해석 전에 기본 보안 필터에서 차단되는지 검증한다. */
@DisplayName("MEET-08 회의 종료 보안")
@SpringBootTest
@AutoConfigureMockMvc
class MeetingCompletionSecurityTest {

    /* 실제 SecurityFilterChain을 통과하는 HTTP 요청을 실행한다. */
    @Autowired
    private MockMvc mockMvc;

    /* 인증 실패 요청이 회의 종료 유스케이스에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private CompleteMeetingUseCase completeMeetingUseCase;

    /* Access Token 없는 종료 요청이 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 POST 종료 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousCompletionRequest() throws Exception {
        /* 인증 헤더 없이 91번 회의 종료 요청을 전송한다. */
        mockMvc.perform(post("/api/v1/meetings/91/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 MEET-08 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(completeMeetingUseCase);
    }
}
