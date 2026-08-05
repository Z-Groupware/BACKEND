package com.module06.backend.identity.auth.presentation.api;

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
import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.dto.LoginResult;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 프론트가 이 키 이름으로 보내고 받는다. 이름이 하나 바뀌면 로그인 화면이 통째로 막히므로
 * 요청·응답 키를 여기서 고정한다.
 */
@DisplayName("AuthController")
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private ReissueTokenUseCase reissueTokenUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("요청 키 4개를 그대로 받아 커맨드로 넘긴다 — companyCode·email·password·keepSignedIn")
    void passesRequestKeysThrough() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new LoginResult("access", "refresh", "/team"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyCode": "8AS2-G8T1",
                                  "email": "hayun@zgroup.co.kr",
                                  "password": "Abcd1234",
                                  "keepSignedIn": true
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase).login(captor.capture());
        LoginCommand command = captor.getValue();

        assertThat(command.companyCode()).isEqualTo("8AS2-G8T1");
        assertThat(command.email()).isEqualTo("hayun@zgroup.co.kr");
        assertThat(command.password()).isEqualTo("Abcd1234");
        assertThat(command.keepSignedIn()).isTrue();
    }

    @Test
    @DisplayName("응답에 accessToken·refreshToken·landingPath 를 담는다. 프로필은 담지 않는다")
    void returnsTokensAndLandingPath() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new LoginResult("eyJaccess", "eyJrefresh", "/my"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode":"8AS2-G8T1","email":"a@b.co","password":"Abcd1234","keepSignedIn":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("eyJaccess"))
                .andExpect(jsonPath("$.data.refreshToken").value("eyJrefresh"))
                .andExpect(jsonPath("$.data.landingPath").value("/my"))
                .andExpect(jsonPath("$.data.name").doesNotExist())
                .andExpect(jsonPath("$.data.mustChangePassword").doesNotExist());
    }

    @Test
    @DisplayName("keepSignedIn 을 빼면 false 로 본다 — 체크박스를 끄면 프론트가 키를 안 보낼 수 있다")
    void missingKeepSignedInDefaultsToFalse() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new LoginResult("access", "refresh", "/my"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode":"8AS2-G8T1","email":"a@b.co","password":"Abcd1234"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase).login(captor.capture());

        assertThat(captor.getValue().keepSignedIn()).isFalse();
    }

    @Test
    @DisplayName("비밀번호를 빼면 400 — 유스케이스를 부르지 않는다")
    void missingPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode":"8AS2-G8T1","email":"a@b.co","keepSignedIn":false}
                                """))
                .andExpect(status().isBadRequest());

        verify(loginUseCase, org.mockito.Mockito.never()).login(any());
    }

    @Test
    @DisplayName("재발급은 refreshToken·keepSignedIn 을 그대로 넘긴다")
    void passesReissueKeysThrough() throws Exception {
        when(reissueTokenUseCase.reissue(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new ReissueTokenUseCase.ReissuedTokens("access", "refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"eyJold","keepSignedIn":true}
                                """))
                .andExpect(status().isOk());

        verify(reissueTokenUseCase).reissue("eyJold", true);
    }

    @Test
    @DisplayName("재발급 응답에 새 토큰 두 개를 담는다 — 착지 경로는 담지 않는다(재발급은 화면을 옮기지 않는다)")
    void returnsReissuedTokens() throws Exception {
        when(reissueTokenUseCase.reissue(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new ReissueTokenUseCase.ReissuedTokens("eyJnewAccess", "eyJnewRefresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"eyJold","keepSignedIn":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("eyJnewAccess"))
                .andExpect(jsonPath("$.data.refreshToken").value("eyJnewRefresh"))
                .andExpect(jsonPath("$.data.landingPath").doesNotExist());
    }

    @Test
    @DisplayName("재발급도 keepSignedIn 을 빼면 false 로 본다 — 원시 boolean 이면 여기서 400 이 난다")
    void missingKeepSignedInOnReissueDefaultsToFalse() throws Exception {
        when(reissueTokenUseCase.reissue(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new ReissueTokenUseCase.ReissuedTokens("access", "refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"eyJold"}
                                """))
                .andExpect(status().isOk());

        verify(reissueTokenUseCase).reissue("eyJold", false);
    }

    @Test
    @DisplayName("refreshToken 을 빼면 400 — 유스케이스를 부르지 않는다")
    void missingRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keepSignedIn":false}
                                """))
                .andExpect(status().isBadRequest());

        verify(reissueTokenUseCase, org.mockito.Mockito.never())
                .reissue(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("로그아웃은 바디가 아니라 토큰의 memberId 로 대상을 정한다 — 남을 로그아웃시킬 수 없다")
    void logoutUsesPrincipalNotBody() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(logoutUseCase).logout(7L);
    }

    /** 필터를 끈 슬라이스 테스트라 컨텍스트를 직접 심는다 — JwtAuthenticationFilterTest 와 같은 방식. */
    private void authenticateAs(Long memberId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, 1L, "MEMBER", false, 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }
}
