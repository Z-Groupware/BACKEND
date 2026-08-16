package com.module06.backend.project.presentation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
    첨부 삭제 권한 경계 — 2026-08-16 에 hasAnyRole('OWNER','ADMIN') + 업로더 본인 검사(AND)를
    hasRole('OWNER') 하나로 통일했다. 그 경계가 실제로 서는지 본다.

    ProjectAttachmentControllerTest 는 @WebMvcTest(addFilters = false) 슬라이스라 @PreAuthorize 가
    아예 안 실린다 — 거기서는 어떤 역할로 불러도 통과한다. 그래서 CompanyActionControllerSecurityTest
    와 같은 방식으로 실 SecurityFilterChain 을 태우고 JwtTokenProvider 로 서명된 토큰을 만든다.
    ROLE_ADMIN 은 authority 값이 아니라 is_admin 겸직 플래그로 붙으므로(JwtAuthenticationFilter:85-87)
    LEADER + isAdmin=true 로 만들어야 실제 겸직자를 재현한다.

    403 이 아니면 인가를 지나 애플리케이션까지 닿았다는 뜻이다 — projectId 100 은 테스트 DB(H2)에
    없는 값이라 통과하면 PJ-001 404 로 떨어진다. 그 구분이 이 테스트가 보려는 전부다
    (CompanyActionControllerSecurityTest 와 동일한 판단 방식).

    연결된 클래스
    - ProjectAttachmentController : 경계를 선언하는 곳(@PreAuthorize)
    - ProjectAttachmentService    : 업로더 검사가 사라진 곳
*/
@DisplayName("첨부 삭제 권한 경계")
@SpringBootTest
@AutoConfigureMockMvc
class ProjectAttachmentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("OWNER는 통과한다 — 인가를 지나 애플리케이션까지 닿는다")
    void ownerReachesApplication() throws Exception {
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "OWNER", false, null));

        mockMvc.perform(delete("/api/projects/100/attachments/10").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PJ-001"));
    }

    @Test
    @DisplayName("LEADER+admin 겸직은 403으로 막힌다 — 이번 변경으로 동작이 바뀌는 유일한 방향")
    void adminLeaderIsForbidden() throws Exception {
        // 전에는 hasAnyRole('OWNER','ADMIN') 이라 여기까지 들어와서 서비스의 업로더 검사에
        // 걸렸다(자기가 올린 첨부면 지울 수 있었다). 이제는 인가 단계에서 끊긴다.
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "LEADER", true, 7L));

        mockMvc.perform(delete("/api/projects/100/attachments/10").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("Z-002"));
    }

    @Test
    @DisplayName("일반 MEMBER는 403으로 막힌다")
    void plainMemberIsForbidden() throws Exception {
        String bearer = bearerFor(new AuthPrincipal(3L, 1L, "MEMBER", false, 7L));

        mockMvc.perform(delete("/api/projects/100/attachments/10").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("Z-002"));
    }

    private String bearerFor(AuthPrincipal principal) {
        return "Bearer " + jwtTokenProvider.createAccessToken(principal);
    }
}
