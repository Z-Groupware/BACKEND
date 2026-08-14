package com.module06.backend.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메서드 시큐리티({@code @PreAuthorize}) 역할 거부가 403 으로 나가는지 확인한다.
 *
 * <p>배경: {@code @PreAuthorize} 는 필터 계층이 아니라 메서드 계층에서 거부한다. 이때 던지는
 * {@code AuthorizationDeniedException} 은 {@code org.springframework.security.access.AccessDeniedException}
 * 의 하위 타입이라 필터의 {@code accessDeniedHandler} 가 아니라 DispatcherServlet 의
 * {@code @RestControllerAdvice} 로 흘러간다. 전에는 그것을 {@code @ExceptionHandler(Exception.class)}
 * catch-all 이 먼저 삼켜 500 + 스택트레이스가 나갔다. 이제 전용 핸들러가 403(Z-002)로 매핑한다.
 *
 * <p>재현 대상은 역할만으로 걸러지는 GET 이다. 소유권·회사 스코프 검사는 서비스 층에 있어 DB 가 필요하지만,
 * {@code @PreAuthorize} 역할 거부는 메서드 본문 이전에 끝나므로 DB 없이 재현된다.
 */
@DisplayName("메서드 시큐리티 역할 거부 — 500 이 아니라 403 인지")
@SpringBootTest
@AutoConfigureMockMvc
class MethodSecurityDenialTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /*
     * OUTSIDER 는 어느 역할 화이트리스트에도 없다 → ROLE_OUTSIDER 로 매핑되고, isAdmin=false 라
     * ROLE_ADMIN 도 붙지 않는다. hasAnyRole('OWNER','LEADER','MEMBER') 는 반드시 거부한다.
     * 토큰 자체는 유효(서명·타입)하므로 필터를 지나 메서드 시큐리티까지 도달한다.
     */
    @Test
    @DisplayName("등록되지 않은 authority 로 보호된 GET 을 치면 403 + Z-002 로 답한다")
    void unregisteredAuthorityGetsForbidden() throws Exception {
        AuthPrincipal outsider = new AuthPrincipal(9L, 1L, "OUTSIDER", false, 2L);
        String bearer = "Bearer " + jwtTokenProvider.createAccessToken(outsider);

        mockMvc.perform(get("/api/rooms").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is(CommonErrorCode.ACCESS_DENIED.getCode())));
    }
}
