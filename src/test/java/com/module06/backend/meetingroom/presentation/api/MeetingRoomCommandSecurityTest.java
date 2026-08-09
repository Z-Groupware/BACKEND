package com.module06.backend.meetingroom.presentation.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.security.test.context.support.WithMockUser;

import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;

/*
 * ROOM-03~05 익명 요청과 ROOM-04·05 역할 위반이 유스케이스 호출 전에 차단되는지 검증한다.
 */
@DisplayName("ROOM-03~05 회의실 명령 보안")
@SpringBootTest
@AutoConfigureMockMvc
class MeetingRoomCommandSecurityTest {

    /* 실제 SecurityFilterChain을 통과하는 HTTP 요청을 실행한다. */
    @Autowired
    private MockMvc mockMvc;

    /* 익명 요청에서 애플리케이션 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private CreateMeetingRoomUseCase createMeetingRoomUseCase;

    /* ROOM-04 인증·역할 실패에서 수정 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private UpdateMeetingRoomUseCase updateMeetingRoomUseCase;

    /* ROOM-05 인증·역할 실패에서 비활성화 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private DeactivateMeetingRoomUseCase deactivateMeetingRoomUseCase;

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

    /* Access Token 없는 ROOM-04 요청이 기본 잠금으로 401인지 검증한다. */
    @Test
    @DisplayName("익명 PATCH 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousUpdateRequest() throws Exception {
        /* 정상 PATCH 본문을 인증 헤더 없이 전송한다. */
        mockMvc.perform(patch("/api/v1/meeting-rooms/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capacity": 10
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 기본 인증 단계에서 거절된 요청은 ROOM-04 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(updateMeetingRoomUseCase);
    }

    /* 로그인했지만 관리 역할이 아닌 사용자가 ROOM-04를 호출할 수 없는지 검증한다. */
    @Test
    @WithMockUser(roles = "MEMBER")
    @DisplayName("MEMBER의 PATCH 요청을 403으로 거절한다")
    void rejectsMemberUpdateRequest() throws Exception {
        /* MEMBER 인증을 가진 상태에서 정상 PATCH 본문을 전송한다. */
        mockMvc.perform(patch("/api/v1/meeting-rooms/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capacity": 10
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MR-004"));

        /* @PreAuthorize에서 거절된 요청은 ROOM-04 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(updateMeetingRoomUseCase);
    }

    /* Access Token 없는 ROOM-05 요청이 기본 잠금으로 401인지 검증한다. */
    @Test
    @DisplayName("익명 DELETE 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousDeactivateRequest() throws Exception {
        /* 인증 헤더 없이 2번 회의실 비활성화 요청을 전송한다. */
        mockMvc.perform(delete("/api/v1/meeting-rooms/2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 기본 인증 단계에서 거절된 요청은 ROOM-05 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(deactivateMeetingRoomUseCase);
    }

    /* 로그인했지만 관리 역할이 아닌 사용자가 ROOM-05를 호출할 수 없는지 검증한다. */
    @Test
    @WithMockUser(roles = "LEADER")
    @DisplayName("LEADER의 DELETE 요청을 MR-004와 403으로 거절한다")
    void rejectsLeaderDeactivateRequest() throws Exception {
        /* LEADER 인증으로 2번 회의실 비활성화 요청을 전송한다. */
        mockMvc.perform(delete("/api/v1/meeting-rooms/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MR-004"));

        /* @PreAuthorize에서 거절된 요청은 ROOM-05 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(deactivateMeetingRoomUseCase);
    }
}
