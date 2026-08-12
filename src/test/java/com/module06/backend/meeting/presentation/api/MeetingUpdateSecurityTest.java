package com.module06.backend.meeting.presentation.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.meeting.application.usecase.UpdateMeetingUseCase;

/*
 * MEET-05 익명 요청이 AuthPrincipal 인자 해석 전에 공통 보안 필터에서 차단되는지 검증한다.
 */
@DisplayName("MEET-05 회의 정보 수정 보안")
@SpringBootTest
@AutoConfigureMockMvc
class MeetingUpdateSecurityTest {

    /* 실제 SecurityFilterChain과 MVC 인자 해석을 통과하는 HTTP 요청 실행기다. */
    @Autowired
    private MockMvc mockMvc;

    /* 인증 실패 요청이 회의 수정 비즈니스 로직에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private UpdateMeetingUseCase updateMeetingUseCase;

    /* Access Token 없는 PATCH 요청이 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 PATCH 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousMeetingUpdateRequest() throws Exception {
        /* 인증 헤더 없이 정상 형태의 제목 수정 본문을 91번 회의에 전송한다. */
        mockMvc.perform(patch("/api/meetings/91")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"변경 시도\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 수정 유스케이스를 한 번도 호출하면 안 된다. */
        verifyNoInteractions(updateMeetingUseCase);
    }
}
