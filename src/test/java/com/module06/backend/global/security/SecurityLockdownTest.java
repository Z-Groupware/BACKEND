package com.module06.backend.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 기본이 잠김인지 확인한다 — 필터를 켠 상태로 돈다.
 *
 * <p>이 테스트가 있어야 하는 이유: 체인의 마지막 줄이 {@code permitAll()} 로 되돌아가면
 * 새로 만든 API 가 아무 설정 없이 무인증으로 열린다. 그건 코드 리뷰에서 놓치기 쉽고
 * 배포 후에도 조용하다. 여기서 깨지게 만들어 둔다.
 *
 * <p>도메인별 컨트롤러 슬라이스 테스트는 {@code addFilters = false} 라 이 규칙을 보지 못한다.
 * 필터 체인을 실제로 태우는 테스트는 이것뿐이다.
 */
@DisplayName("전체 잠금 — 기본이 인증 필수인지")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityLockdownTest {

    /** 보안 필터가 막았을 때 SecurityErrorResponder 가 쓰는 코드(AuthErrorCode.UNAUTHORIZED). */
    private static final String SECURITY_BLOCKED_CODE = "AU-006";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /*
     * "401 이 아님" 으로는 판정할 수 없다 — 로그인 실패(AU-002)·갱신표 불량(AU-004)도 401 이다.
     * 구분은 누가 답했는지로 한다. 보안 필터가 막으면 SecurityErrorResponder 가 AU-006 을 쓰므로,
     * 응답에 AU-006 이 없으면 요청이 컨트롤러까지 갔다는 뜻이다.
     */
    @Test
    @DisplayName("로그인 전 화면에 필요한 경로는 토큰 없이 통과한다")
    void loginFlowStaysOpen() throws Exception {
        assertReachesApplication(post("/api/companies/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"NOPE-0000\"}"));
        assertReachesApplication(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyCode\":\"X\",\"email\":\"a@b.co\",\"password\":\"p\"}"));
        assertReachesApplication(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"x\"}"));
    }

    /*
     * 200 을 기대하지 않는다 — 테스트 환경에는 Redis 가 없어 health 가 503(DOWN)을 준다.
     * 여기서 확인할 것은 "보안이 막지 않는다" 이고, 그건 401 이 아니라는 것으로 충분하다.
     */
    @Test
    @DisplayName("헬스체크는 열려 있다 — 배포·로드밸런서가 토큰 없이 부른다")
    void healthStaysOpen() throws Exception {
        assertNotUnauthorized(get("/actuator/health"));
    }

    @Test
    @DisplayName("API 문서는 열려 있다 — 스펙을 잠그면 Swagger UI 가 못 읽는다")
    void apiDocsStayOpen() throws Exception {
        assertNotUnauthorized(get("/v3/api-docs"));
        assertNotUnauthorized(get("/swagger-ui/index.html"));
    }

    /*
     * 지표는 잠근다. 스크래핑하는 주체가 아직 없어 막아도 깨지는 것이 없고, 엔드포인트 이름·호출 수·
     * 메모리 같은 내부 정보가 그대로 나간다. 모니터링을 붙일 때 네트워크 단(VPC·보안그룹)에서
     * 열거나 토큰을 주는 편이 맞다.
     */
    @Test
    @DisplayName("지표 엔드포인트는 잠겨 있다")
    void metricsAreClosed() throws Exception {
        assertUnauthorized(get("/actuator/prometheus"));
    }

    @Test
    @DisplayName("인증이 필요한 인증 API 는 토큰 없이 거절된다")
    void authenticatedAuthApisAreClosed() throws Exception {
        assertUnauthorized(get("/api/auth/me"));
        assertUnauthorized(post("/api/auth/logout"));
    }

    /*
     * 핵심이다. 이 경로들은 SecurityConfig 에 개별로 등록돼 있지 않다 — 마지막 줄이
     * authenticated() 라서 막히는 것이다. permitAll() 로 되돌아가면 여기서 깨진다.
     */
    @Test
    @DisplayName("개별 등록이 없는 업무 API 도 토큰 없이 거절된다 — 기본이 잠김이라는 증거")
    void businessApisAreClosedByDefault() throws Exception {
        assertUnauthorized(get("/api/projects"));
        assertUnauthorized(get("/api/handovers?teamId=1"));
        assertUnauthorized(patch("/api/handovers/1/finalize"));
        assertUnauthorized(get("/api/rooms"));
        assertUnauthorized(get("/api/meetings/upcoming"));
    }

    @Test
    @DisplayName("존재하지 않는 경로도 토큰 없이는 거절된다 — 앞으로 만들 API 가 자동으로 보호된다")
    void unknownPathsAreClosedToo() throws Exception {
        assertUnauthorized(get("/api/quality/metrics"));
        assertUnauthorized(post("/api/meetings/1/vocabulary/rebuild"));
    }

    /*
     * 401 만으로는 부족하다 — 컨트롤러나 다른 핸들러가 낸 401 도 통과해 버린다. 위 주석의 판정 기준을
     * 막힌 쪽에도 그대로 적용한다: 보안 필터가 막았다면 응답에 AU-006 이 있어야 한다.
     */
    /*
     * 반대 방향의 증거다. 위 테스트가 전부 통과해도 아무도 못 들어오면 그건 잠긴 것이 아니라 고장이다.
     * 그 사고를 볼 수 있는 곳이 여기뿐이다 — 컨트롤러 슬라이스는 addFilters = false 라 체인을 안 태우고,
     * JwtAuthenticationFilterTest 는 필터를 MockFilterChain 으로 단독 실행해 시큐리티 체인 밖이다.
     * 토큰이 실제 체인을 지나 애플리케이션까지 닿는지는 아무도 보지 않고 있었다.
     */
    @Test
    @DisplayName("유효한 토큰은 보호 경로를 통과한다 — 잠긴 것이지 닫힌 것이 아니다")
    void validTokenReachesProtectedApis() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(3L, 1L, "MEMBER", false, 2L);
        String bearer = "Bearer " + jwtTokenProvider.createAccessToken(principal);

        // 개별 등록이 있는 경로와 기본 잠금으로만 막히는 경로를 각각 본다.
        // 컨트롤러가 무엇으로 답하든(404·403·500) 상관없다 — AU-006 이 아니면 필터를 지났다는 뜻이다.
        assertReachesApplication(get("/api/auth/me").header("Authorization", bearer));
        assertReachesApplication(get("/api/projects").header("Authorization", bearer));
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("토큰 없이 부르면 401 이어야 한다: %s", request)
                        .isEqualTo(401))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("401 을 낸 것이 보안 필터여야 한다 — 컨트롤러가 낸 401 은 잠금의 증거가 아니다: %s",
                                request)
                        .contains(SECURITY_BLOCKED_CODE));
    }

    private void assertNotUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("공개 경로는 401 이 아니어야 한다(다른 실패는 무관)")
                        .isNotEqualTo(401));
    }

    /** 보안 필터가 아니라 애플리케이션이 답했는지 — 실패해도 좋으니 AU-006 이 아니면 통과다. */
    private void assertReachesApplication(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("보안 필터가 막았다 — 공개 경로여야 한다")
                        .doesNotContain(SECURITY_BLOCKED_CODE));
    }
}
