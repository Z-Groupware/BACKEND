package com.module06.backend.global.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.module06.backend.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

// @Component 를 붙이지 않는다 — SecurityConfig 가 @Bean 으로 등록한다.
// @WebMvcTest 는 @Component 를 스캔하지 않으면서 SecurityConfig 는 로드하므로,
// 컴포넌트 스캔에 의존하면 컨트롤러 슬라이스 테스트가 전부 빈 부족으로 깨진다.
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    /**
     * SSE 같은 비동기 응답은 컨트롤러가 반환한 뒤 ASYNC 디스패치로 필터 체인을 한 번 더 탄다.
     * {@code OncePerRequestFilter} 는 그 두 번째 통과를 기본으로 건너뛰지만
     * ({@code shouldNotFilterAsyncDispatch()} 기본값 true), Spring Security 6 의
     * {@code AuthorizationFilter} 는 모든 디스패치 타입에서 돈다.
     *
     * <p>그래서 재정의하지 않으면 ASYNC 통과에서 <b>인증은 안 걸고 인가만 거는</b> 상태가 되어
     * 이미 열린 스트림이 {@code AuthorizationDeniedException} 으로 끊긴다. 세션이 STATELESS 라
     * SecurityContext 가 살아남는 곳도 없으므로, 같은 요청의 Authorization 헤더를 다시 읽어
     * 컨텍스트를 재구성하는 것이 유일한 복원 경로다.
     *
     * <p>인증을 우회하는 것이 아니다 — 같은 토큰을 같은 규칙으로 한 번 더 검증한다.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        resolveToken(request).ifPresent(this::authenticate);
        chain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void authenticate(String token) {
        try {
            AuthPrincipal principal = tokenProvider.parseAccessToken(token);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities(principal)));
        } catch (BusinessException e) {

            SecurityContextHolder.clearContext();
        }
    }

    /**
     * authority 이름의 {@code ROLE_} 접두사는 그대로 둔다 — {@code hasRole()} 이 그 규약으로 찾는다.
     * 우리 도메인의 이름만 authority 로 바꿨고 Spring Security 표기는 건드리지 않는다.
     */
    private List<GrantedAuthority> authorities(AuthPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.authority()));
        if (principal.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }
}
