package com.module06.backend.global.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityErrorResponder securityErrorResponder(ObjectMapper objectMapper) {
        return new SecurityErrorResponder(objectMapper);
    }

    /*
     * 토큰은 쿠키가 아니라 응답 바디로 내려가 프론트가 Authorization 헤더에 직접 실어 보낸다 —
     * 그래서 allowCredentials 는 필요 없다(브라우저 쿠키를 안 쓴다). 허용 오리진은
     * app.cors.allowed-origins 에서만 관리한다 — 배포 도메인이 늘어도 이 클래스는 안 건드린다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   SecurityErrorResponder securityErrorResponder,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder))
                .authorizeHttpRequests(auth -> auth
                        // ── 공개: 로그인 전 화면 ──
                        // 토큰을 받으러 오는 경로다. 여기에 인증을 걸면 아무도 로그인할 수 없다.
                        // refresh 는 갱신표 자체가 신분증이라 액세스 토큰을 요구하지 않는다.
                        // registrations 는 회사가 아직 없는 사람이 부른다 — 토큰을 줄 계정 자체가
                        // 없으므로 인증을 걸면 아무도 회사를 만들 수 없다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/companies/lookup",
                                "/api/companies/registrations",
                                "/api/auth/login",
                                "/api/auth/refresh").permitAll()

                        // ── 공개: 운영·문서 ──
                        // health 는 배포·로드밸런서가 토큰 없이 부른다(show-details: never 라 내부 정보는 안 나간다).
                        // API 문서는 스펙을 잠그면 Swagger UI 가 읽지 못해 화면 자체가 죽는다 —
                        // 운영에서 감추려면 보안 규칙이 아니라 springdoc.api-docs.enabled=false 로 끈다.
                        // /actuator/prometheus 는 일부러 열지 않는다. 스크래핑 주체가 아직 없어 막아도
                        // 깨지지 않고, 엔드포인트 이름·호출 수·메모리가 그대로 나간다. 모니터링을 붙일 때
                        // 네트워크 단(VPC·보안그룹)에서 열거나 토큰을 주는 편이 맞다.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()

                        // ── 기본 잠금 ──
                        // 개별 등록을 지웠다. 전까지는 체인이 permitAll() 로 끝나서 담당자가 자기
                        // 엔드포인트를 여기 적어야 익명 요청이 401 이 됐고(로그아웃·내 정보·CAP 청크·
                        // 캡처 파이프라인 3개·ROOM-03), 그 목록은 API 가 늘수록 길어지기만 했다.
                        // 지금은 기본이 잠김이라 전부 필요 없다.
                        //
                        // 방향이 뒤집힌 것이 핵심이다. 전에는 등록을 빼먹으면 그 API 가 조용히 열렸고,
                        // 이제는 공개 예외를 빼먹으면 401 이 나서 바로 발견된다.
                        // 역할·소유권 검사는 각 컨트롤러의 @PreAuthorize 와 서비스가 계속 맡는다 —
                        // 이 줄은 "로그인했나" 까지만 본다.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
