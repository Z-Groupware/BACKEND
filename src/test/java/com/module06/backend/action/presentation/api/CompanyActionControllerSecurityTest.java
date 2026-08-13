package com.module06.backend.action.presentation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 2026-08-13, 종준님(PO) 확정 DoD 3케이스 — OWNER 성공·일반 LEADER(admin 아님) 거부·LEADER+admin
 * 겸직 성공. @WebMvcTest 슬라이스(addFilters=false)로는 @PreAuthorize가 안 실려서(TeamActionControllerTest
 * 주석과 동일 이유) SecurityLockdownTest와 같은 방식으로 실 SecurityFilterChain을 태운다 —
 * JwtTokenProvider로 실제 서명된 토큰을 만들어 principal.isAdmin() SpEL이 진짜로 평가되는지 본다.
 *
 * ActionService가 CreateActionUseCase 등 여러 인터페이스를 구현하는 단일 빈이라, @MockitoBean으로
 * GetCompanyMemberActionsUseCase만 목킹하면 같은 빈을 쓰는 ActionController가 통째로 깨진다
 * (Bean named 'actionService' is expected to be of type 'CreateActionUseCase' but was MockitoMock).
 * 그래서 여기서는 목 없이 실 서비스를 통과시키고, "403으로 막히는가"만으로 권한 경계를 본다 —
 * assigneeMemberId=9는 테스트 DB(H2, 트랜잭션 롤백)에 없는 값이라 인가를 통과해도 404
 * (ACTION_ASSIGNEE_NOT_FOUND)로 응답한다. 그게 이 테스트가 확인하려는 것과 다르지 않다 — 403이
 * 아니면 @PreAuthorize를 지나 애플리케이션까지 닿았다는 뜻이다(SecurityLockdownTest와 동일 판단 방식).
 */
@DisplayName("CompanyActionController 권한 경계")
@SpringBootTest
@AutoConfigureMockMvc
class CompanyActionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("OWNER는 통과한다 — 인가를 지나 애플리케이션까지 닿는다")
    void ownerReachesApplication() throws Exception {
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "OWNER", false, null));

        mockMvc.perform(get("/api/company/actions").param("assigneeMemberId", "9").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AC-008"));
    }

    @Test
    @DisplayName("일반 LEADER(admin 아님)는 403으로 막힌다")
    void plainLeaderIsForbidden() throws Exception {
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "LEADER", false, 7L));

        mockMvc.perform(get("/api/company/actions").param("assigneeMemberId", "9").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AC-015"));
    }

    @Test
    @DisplayName("LEADER+admin 겸직은 통과한다")
    void adminLeaderReachesApplication() throws Exception {
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "LEADER", true, 7L));

        mockMvc.perform(get("/api/company/actions").param("assigneeMemberId", "9").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AC-008"));
    }

    @Test
    @DisplayName("일반 MEMBER는 403으로 막힌다")
    void plainMemberIsForbidden() throws Exception {
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "MEMBER", false, 7L));

        mockMvc.perform(get("/api/company/actions").param("assigneeMemberId", "9").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AC-015"));
    }

    private String bearerFor(AuthPrincipal principal) {
        return "Bearer " + jwtTokenProvider.createAccessToken(principal);
    }
}
