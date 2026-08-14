package com.module06.backend.global.security;

import java.time.Duration;

import jakarta.servlet.DispatcherType;

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
        provider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30)));
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

    /*
     * 아래 세 건은 SSE(알림·자막)가 운영에서 AuthorizationDeniedException 으로 끊기던 회귀를 막는다.
     *
     * SseEmitter 를 반환하면 컨트롤러가 끝난 뒤 ASYNC 디스패치로 필터 체인을 한 번 더 타는데,
     * OncePerRequestFilter 는 그 통과를 기본으로 건너뛰는 반면 Spring Security 6 의
     * AuthorizationFilter 는 모든 디스패치 타입에서 돈다. 세션도 STATELESS 라 SecurityContext 가
     * 살아남지 않는다 — 그래서 "인증은 없고 인가만 있는" 상태가 되어 열린 스트림이 끊겼다.
     */

    @Test
    @DisplayName("ASYNC 디스패치에서도 헤더를 다시 읽어 인증을 복원한다 — SSE 가 여기서 끊겼다")
    void restoresAuthenticationOnAsyncDispatch() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(3L, 1L, "MEMBER", false, 2L);
        MockHttpServletRequest request = requestWithToken(principal);
        request.setDispatcherType(DispatcherType.ASYNC);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(principal);
    }

    @Test
    @DisplayName("ASYNC 디스패치에서 토큰이 만료됐으면 아무것도 심지 않는다 — 재검증이지 무조건 통과가 아니다")
    void clearsContextOnAsyncDispatchWithExpiredToken() throws Exception {
        // accessTtl 이 음수면 발급 즉시 만료된 토큰이 나온다.
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(-1), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization",
                "Bearer " + expiredProvider.createAccessToken(new AuthPrincipal(3L, 1L, "MEMBER", false, 2L)));
        request.setDispatcherType(DispatcherType.ASYNC);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("shouldNotFilterAsyncDispatch 는 false 여야 한다 — 이 재정의를 지우면 SSE 가 다시 끊긴다")
    void doesNotSkipAsyncDispatch() {
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }
}
