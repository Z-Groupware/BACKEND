package com.module06.backend.global.security;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtTokenProvider provider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(new JwtProperties(
                SECRET, Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14)));
        filter = new JwtAuthenticationFilter(provider);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(AuthPrincipal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createAccessToken(principal));
        return request;
    }

    @Test
    @DisplayName("유효한 토큰이면 AuthPrincipal 을 심는다")
    void putsPrincipalIntoContext() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(3L, 1L, "MEMBER", false, 2L);

        filter.doFilter(requestWithToken(principal), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(principal);
    }

    @Test
    @DisplayName("어드민 겸직 팀장은 ROLE_LEADER 와 ROLE_ADMIN 을 둘 다 갖는다")
    void adminLeaderHasBothAuthorities() throws Exception {
        filter.doFilter(requestWithToken(new AuthPrincipal(2L, 1L, "LEADER", true, 5L)),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_LEADER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("어드민이 아닌 사원은 ROLE_MEMBER 만 갖는다")
    void plainMemberHasSingleAuthority() throws Exception {
        filter.doFilter(requestWithToken(new AuthPrincipal(3L, 1L, "MEMBER", false, 2L)),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("헤더가 없으면 아무것도 심지 않고 통과시킨다 — 공개 경로도 이 필터를 지난다")
    void passesThroughWithoutHeader() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("망가진 토큰이면 심지 않고 통과시킨다 — 거부는 인가 단계가 한다")
    void passesThroughWithBrokenToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-jwt");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
