package com.module06.backend.global.security;

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

import tools.jackson.databind.ObjectMapper;

// 인증 배선 전체를 이 클래스 하나에 모은다.
//
// JwtTokenProvider·JwtAuthenticationFilter 를 @Component 로 두지 않고 여기서 @Bean 으로 등록하는
// 이유: @WebMvcTest 는 컴포넌트 스캔을 하지 않으면서 보안 설정은 로드한다. 스캔에 의존하면
// 팀원들의 컨트롤러 슬라이스 테스트가 전부 "JwtTokenProvider 빈 없음"으로 깨지고, 각 테스트에
// @MockitoBean 을 추가해 달라고 부탁해야 한다. 실제로 HandoverControllerTest 5개가 그렇게 깨졌다.
//
// jwt.* 바인딩도 @EnableConfigurationProperties 로 여기서 켠다 — 슬라이스 테스트에서도 같은
// 경로로 해결되게 하려는 것이다.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    // 스프링 기본 구현은 본문 없는 403 을 내린다 — 프론트와 약속한 ErrorResponse 계약이 깨진다.
    @Bean
    public SecurityErrorResponder securityErrorResponder(ObjectMapper objectMapper) {
        return new SecurityErrorResponder(objectMapper);
    }

    // 1단계 — 인증 경로만 규칙을 걸고 나머지는 계속 열어둔다.
    //
    // 여기서 anyRequest().authenticated() 로 뒤집으면 handover·project·action·meetingroom API 가
    // 동시에 토큰을 요구해 다른 담당자들의 작업이 멈춘다. 전체 잠금은 팀에 예고한 날짜에
    // 별도 커밋으로 한다.
    //
    // 최종 상태는 "기본 잠금 + 공개 예외 명시" 여야 한다. 기본이 열림이면 새 API 가 아무 설정
    // 없이 열리고, 실수가 조용한 보안 구멍이 된다. 기본이 잠김이면 설정을 빼먹었을 때 401 이
    // 나서 바로 발견된다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   SecurityErrorResponder securityErrorResponder) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/companies/lookup",
                                "/api/auth/login",
                                "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        // 그 외는 아직 열어둔다 — 전체 잠금은 팀 공지 후 별도 커밋에서 뒤집는다.
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // 비밀번호는 발급 시 한 번 해싱해 저장하고 변경 기능이 없다.
    // 로그인은 이 인코더의 matches() 로만 비교한다 — 평문 비교 경로를 만들지 않는다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
