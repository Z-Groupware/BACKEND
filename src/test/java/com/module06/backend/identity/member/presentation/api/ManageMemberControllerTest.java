package com.module06.backend.identity.member.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.dto.IssuedMember;
import com.module06.backend.identity.member.application.usecase.IssueMemberUseCase;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;

/*
 * 계정 발급(§5-1)의 요청·응답 키를 고정한다. 특히 응답에 비밀번호가 섞이면 발급자가 남의 계정으로
 * 로그인할 수 있게 되므로, 그 회귀를 여기서 막는다 — 비밀번호는 메일로만 나간다.
 */
@DisplayName("ManageMemberController")
@WebMvcTest(ManageMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class ManageMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueMemberUseCase issueMemberUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("요청 키 6개를 그대로 커맨드로 넘긴다 — name·email·teamId·jobPositionId·role·roleLabel")
    void passesRequestKeysThrough() throws Exception {
        authenticateAs(1L);
        when(issueMemberUseCase.issue(any())).thenReturn(issued());

        mockMvc.perform(post("/api/manage/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","email":"name@company.kr",
                                 "teamId":2,"jobPositionId":4,"role":"MEMBER","roleLabel":null}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<IssueMemberCommand> captor = ArgumentCaptor.forClass(IssueMemberCommand.class);
        verify(issueMemberUseCase).issue(captor.capture());
        IssueMemberCommand command = captor.getValue();

        assertThat(command.name()).isEqualTo("홍길동");
        assertThat(command.email()).isEqualTo("name@company.kr");
        assertThat(command.teamId()).isEqualTo(2L);
        assertThat(command.jobPositionId()).isEqualTo(4L);
        assertThat(command.role()).isEqualTo(Authority.MEMBER);
        assertThat(command.roleLabel()).isNull();
    }

    @Test
    @DisplayName("발급 대상 회사는 바디가 아니라 토큰에서 온다 — 남의 회사에 계정을 만들 수 없다")
    void takesCompanyFromToken() throws Exception {
        authenticateAs(55L);
        when(issueMemberUseCase.issue(any())).thenReturn(issued());

        mockMvc.perform(post("/api/manage/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","email":"name@company.kr",
                                 "teamId":2,"jobPositionId":4,"role":"MEMBER","companyId":1}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<IssueMemberCommand> captor = ArgumentCaptor.forClass(IssueMemberCommand.class);
        verify(issueMemberUseCase).issue(captor.capture());

        /* 바디에 companyId 를 넣어도 토큰 값이 이긴다. */
        assertThat(captor.getValue().companyId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("응답 키 8개를 고정하고 비밀번호는 절대 담지 않는다 — 메일로만 나간다")
    void neverReturnsPassword() throws Exception {
        authenticateAs(1L);
        when(issueMemberUseCase.issue(any())).thenReturn(issued());

        mockMvc.perform(post("/api/manage/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","email":"name@company.kr",
                                 "teamId":2,"jobPositionId":4,"role":"MEMBER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(11))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.email").value("name@company.kr"))
                .andExpect(jsonPath("$.data.teamName").value("개발팀"))
                .andExpect(jsonPath("$.data.positionName").value("사원"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.isAdmin").value(false))
                .andExpect(jsonPath("$.data.workStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400 — 유스케이스를 부르지 않는다")
    void malformedEmailIsRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/manage/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","email":"not-an-email",
                                 "teamId":2,"jobPositionId":4,"role":"MEMBER"}
                                """))
                .andExpect(status().isBadRequest());

        verify(issueMemberUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("부서·직급·권한을 빼면 400 — 화면 select 를 안 고르고 보낼 수 없다")
    void missingRequiredSelectionsAreRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/manage/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","email":"name@company.kr"}
                                """))
                .andExpect(status().isBadRequest());

        verify(issueMemberUseCase, never()).issue(any());
    }

    private static IssuedMember issued() {
        return new IssuedMember(11L, "홍길동", "name@company.kr", "개발팀", "사원",
                Authority.MEMBER, false, MemberStatus.ACTIVE);
    }

    /*
     * 발급자는 어드민 겸직자다. authority 에 "ADMIN" 을 넣지 않는 이유가 있다 — Authority 는
     * OWNER·LEADER·MEMBER 3값이고 어드민은 값이 아니라 isAdmin 겸직 플래그다(V2.2.3).
     * ROLE_ADMIN 은 JwtAuthenticationFilter 가 그 플래그를 보고 따로 얹는다.
     */
    private void authenticateAs(Long companyId) {
        AuthPrincipal principal = new AuthPrincipal(3L, companyId, "MEMBER", true, 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
