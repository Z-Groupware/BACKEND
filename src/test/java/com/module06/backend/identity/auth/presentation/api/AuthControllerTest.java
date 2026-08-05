package com.module06.backend.identity.auth.presentation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.dto.LoginResult;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;

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
}
