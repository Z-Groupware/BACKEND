package com.module06.backend.meeting.presentation.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.module06.backend.meeting.application.usecase.GetCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.PauseCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.ResumeCaptureSessionUseCase;

/*
 * CAP-01~03·10 익명 요청이 AuthPrincipal 인자 해석 전에 기본 보안 필터에서 차단되는지 검증한다.
 */
@DisplayName("CAP-01~03·10 캡처 세션 보안")
@SpringBootTest
@AutoConfigureMockMvc
class CaptureSessionSecurityTest {

    /* 실제 SecurityFilterChain을 통과하는 HTTP 요청을 실행한다. */
    @Autowired
    private MockMvc mockMvc;

    /* 인증 실패 요청이 캡처 세션 시작 유스케이스에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private StartCaptureSessionUseCase startCaptureSessionUseCase;

    /* 인증 실패 요청이 캡처 일시정지 유스케이스에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private PauseCaptureSessionUseCase pauseCaptureSessionUseCase;

    /* 인증 실패 요청이 캡처 재개 유스케이스에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private ResumeCaptureSessionUseCase resumeCaptureSessionUseCase;

    /* 인증 실패 요청이 현재 캡처 세션 조회 유스케이스에 도달하지 않는지 확인하는 대역이다. */
    @MockitoBean
    private GetCaptureSessionUseCase getCaptureSessionUseCase;

    /* Access Token 없는 캡처 시작 요청이 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 POST 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousCaptureSessionRequest() throws Exception {
        /* 인증 헤더 없이 91번 회의 캡처 세션 시작 요청을 전송한다. */
        mockMvc.perform(post("/api/v1/meetings/91/capture-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 CAP-01 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(startCaptureSessionUseCase);
    }

    /* Access Token 없는 일시정지 요청이 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 POST 일시정지 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousPauseRequest() throws Exception {
        /* 인증 헤더 없이 91번 회의 캡처 일시정지 요청을 전송한다. */
        mockMvc.perform(post("/api/v1/meetings/91/capture-session/pause"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 CAP-02 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(pauseCaptureSessionUseCase);
    }

    /* Access Token 없는 재개 요청이 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 POST 재개 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousResumeRequest() throws Exception {
        /* 인증 헤더 없이 91번 회의 캡처 재개 요청을 전송한다. */
        mockMvc.perform(post("/api/v1/meetings/91/capture-session/resume"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 CAP-03 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(resumeCaptureSessionUseCase);
    }

    /* Access Token 없는 현재 세션 조회가 Controller 전에 공통 401로 차단되는지 검증한다. */
    @Test
    @DisplayName("익명 GET 현재 세션 조회를 AU-006과 401로 거절한다")
    void rejectsAnonymousCurrentSessionRequest() throws Exception {
        /* 인증 헤더 없이 91번 회의의 현재 캡처 세션 조회 요청을 전송한다. */
        mockMvc.perform(get("/api/v1/meetings/91/capture-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 필터에서 거절된 요청은 CAP-10 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(getCaptureSessionUseCase);
    }
}
