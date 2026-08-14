package com.module06.backend.cap.presentation.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.module06.backend.cap.application.usecase.SubscribeToCaptionsUseCase;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;

/*
 * 자막 SSE(GET /api/meetings/{meetingId}/captions/stream)의 보안 진입 관문 회귀 테스트.
 * 알림 SSE 와 컨트롤러·레지스트리 구조가 동일하다(SseEmitter(0L) + 20초 heartbeat) — 같은 진입 관문을 지킨다.
 *
 * 알림과 달리 자막 구독은 보안 관문을 통과한 뒤 host 여부·회의 존재를 서비스에서 검증한다. 이 테스트는
 * "인증이 스트림을 여느냐"만 보므로 그 업무 검증을 @MockBean 으로 격리한다 — 실제 host/회의 시드가 없어도
 * 200 이 인증 통과의 결과임을 결정적으로 확인한다. host·권한 판정 자체는 서비스 단위 테스트의 몫이다.
 *
 * asyncDispatch 금지·403(500 갭) 제외 근거는 NotificationStreamSecurityTest 주석과 동일하다.
 */
@SpringBootTest
@DisplayName("자막 SSE 스트림 보안 진입 관문")
class CaptionStreamSecurityTest {

    private static final String STREAM_PATH = "/api/meetings/1/captions/stream";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SubscribeToCaptionsUseCase subscribeToCaptionsUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401 — 익명은 스트림을 열 수 없다")
    void streamWithoutAuthorizationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(STREAM_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 MEMBER JWT 면 200 + text/event-stream — 인증된 요청은 스트림을 연다")
    void streamWithMemberTokenReturnsSse() throws Exception {
        // 보안 관문 통과 이후의 host/회의 검증은 이 테스트 범위 밖 — 통과했을 때 SSE 가 열리는지만 본다.
        given(subscribeToCaptionsUseCase.subscribeToCaptions(anyLong(), anyLong()))
                .willReturn(new SseEmitter(0L));

        mockMvc.perform(get(STREAM_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(new AuthPrincipal(7L, 1L, "MEMBER", false, 2L))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    private String bearerToken(AuthPrincipal principal) {
        return "Bearer " + jwtTokenProvider.createAccessToken(principal);
    }
}
