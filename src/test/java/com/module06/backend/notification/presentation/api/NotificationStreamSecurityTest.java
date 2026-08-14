package com.module06.backend.notification.presentation.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;

/*
 * 알림 SSE(GET /api/notifications/stream)의 보안 진입 관문 회귀 테스트.
 *
 * 이 파일이 지키는 것 — 인증 없는 요청은 스트림을 못 열고(401), 인증된 요청은 연다(200 + text/event-stream).
 * 운영에서 ASYNC 디스패치가 열린 스트림을 끊던 사고(JwtAuthenticationFilter.shouldNotFilterAsyncDispatch)
 * 의 재현·복원 자체는 JwtAuthenticationFilterTest(필터 단위)가 맡는다 — SseEmitter(0L)은 타임아웃이 없어
 * MockMvc의 asyncDispatch가 영원히 블록하므로 여기서는 호출하지 않는다(진입 관문까지만 본다).
 *
 * 역할 미달(예: 미등록 authority) 케이스는 여기서 다루지 않는다 — @PreAuthorize 거부가 던지는
 * AuthorizationDeniedException 이 현재 GlobalExceptionHandler 의 catch-all 로 잡혀 403 이 아니라 500 이
 * 된다. 이는 알림 SSE 만의 문제가 아니라 @PreAuthorize 를 쓰는 모든 엔드포인트에 걸친 별개의 갭이라,
 * 그 수정과 회귀 테스트는 별도 작업으로 분리한다(이 PR 은 프로덕션 코드를 건드리지 않는다).
 */
@SpringBootTest
@DisplayName("알림 SSE 스트림 보안 진입 관문")
class NotificationStreamSecurityTest {

    private static final String STREAM_PATH = "/api/notifications/stream";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
        mockMvc.perform(get(STREAM_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(new AuthPrincipal(7L, 1L, "MEMBER", false, 2L))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    private String bearerToken(AuthPrincipal principal) {
        return "Bearer " + jwtTokenProvider.createAccessToken(principal);
    }
}
