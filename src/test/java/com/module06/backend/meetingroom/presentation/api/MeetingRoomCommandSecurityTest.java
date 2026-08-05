package com.module06.backend.meetingroom.presentation.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;

/*
 * ROOM-03 익명 요청이 principal 인자 해석 전에 보안 필터에서 차단되는지 검증한다.
 */
@DisplayName("ROOM-03 회의실 등록 보안")
@SpringBootTest
@AutoConfigureMockMvc
class MeetingRoomCommandSecurityTest {

    /* 실제 SecurityFilterChain을 통과하는 HTTP 요청을 실행한다. */
    @Autowired
    private MockMvc mockMvc;

    /* 익명 요청에서 애플리케이션 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private CreateMeetingRoomUseCase createMeetingRoomUseCase;

    /* Access Token 없는 등록 요청이 500이 아니라 공통 401 응답인지 검증한다. */
    @Test
    @DisplayName("익명 POST 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousCreateRequestBeforePrincipalResolution() throws Exception {
        /* Bean Validation을 통과할 수 있는 정상 본문을 인증 헤더 없이 전송한다. */
        mockMvc.perform(post("/api/v1/meeting-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "대회의실",
                                  "location": "박애관 421호",
                                  "capacity": 12,
                                  "availableFrom": "09:00",
                                  "availableTo": "18:00"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 실패 요청은 Controller와 등록 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(createMeetingRoomUseCase);
    }
}
