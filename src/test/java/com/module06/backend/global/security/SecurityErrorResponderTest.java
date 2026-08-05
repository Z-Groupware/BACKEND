package com.module06.backend.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 시큐리티 단계의 거절도 GlobalExceptionHandler 와 같은 모양으로 나가는지 본다.
 *
 * 이 경로는 필터가 BusinessException 을 삼키고 통과시키기 때문에 GlobalExceptionHandler 를 타지
 * 않는다. 등록을 빼먹으면 스프링 기본 구현이 본문 없는 403 을 내리는데, 그게 조용히 지나가지
 * 않도록 여기서 잡는다.
 */
@DisplayName("SecurityErrorResponder")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorResponderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("토큰 없이 보호 경로를 부르면 401 과 AU-006 을 ErrorResponse 형식으로 내린다")
    void missingTokenYieldsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("망가진 토큰도 같은 401 계약으로 내린다 — 필터가 삼키고 인가 단계가 거절한다")
    void brokenTokenYieldsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));
    }

    @Test
    @DisplayName("권한 부족은 403 과 Z-002 로 내린다 — @PreAuthorize 가 막는 경우")
    void accessDeniedYieldsForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityErrorResponder(objectMapper)
                .handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"Z-002\"");
    }
}
