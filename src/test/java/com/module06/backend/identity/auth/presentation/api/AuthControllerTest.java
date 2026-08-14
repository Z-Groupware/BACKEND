package com.module06.backend.identity.auth.presentation.api;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.audit.AuthzAuditLogger;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.auth.application.dto.LoginResult;
import com.module06.backend.identity.auth.application.command.ChangePasswordCommand;
import com.module06.backend.identity.auth.application.usecase.ChangeMyPasswordUseCase;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.usecase.GetMyProfileUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMyProfileUseCase;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Authority;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    private GetMyProfileUseCase getMyProfileUseCase;

    @MockitoBean
    private UpdateMyProfileUseCase updateMyProfileUseCase;

    @MockitoBean
    private ChangeMyPasswordUseCase changeMyPasswordUseCase;

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

    /*
     * 감사 기록은 GlobalExceptionHandler 가 남긴다. @WebMvcTest 슬라이스가 @RestControllerAdvice 를
     * 등록하므로 여기서 그 배선이 실제로 도는지 볼 수 있다 — 로거 단위 테스트만으로는
     * "핸들러가 부르지 않는다"를 못 잡는다(P1 #5 가 그 상태였다).
     */
    @Test
    @DisplayName("로그인 실패는 감사 로그로 남는다 — 막지도 알지도 못하는 상태에서 벗어난다")
    void loginFailureIsAudited() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuthzAuditLogger.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            when(loginUseCase.login(any())).thenThrow(new BusinessException(AuthErrorCode.LOGIN_FAILED));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyCode":"8AS2-G8T1","email":"a@b.co.kr","password":"wrong"}
                                    """))
                    .andExpect(status().isUnauthorized());

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("outcome=AUTH_FAILED")
                    .contains("path=/api/auth/login")
                    .contains("code=AU-002")
                    // 시도한 계정은 남기지 않는다 — 감사 로그가 계정 목록이 되면 안 된다.
                    .doesNotContain("a@b.co.kr");
        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("재발급은 refreshToken 만 넘긴다 — 수명 선택은 표에서 읽으므로 바디에서 받지 않는다")
    void passesReissueKeysThrough() throws Exception {
        when(reissueTokenUseCase.reissue(any()))
                .thenReturn(new ReissueTokenUseCase.ReissuedTokens("access", "refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"eyJold"}
                                """))
                .andExpect(status().isOk());

        verify(reissueTokenUseCase).reissue("eyJold");
    }

    @Test
    @DisplayName("재발급 응답에 새 토큰 두 개를 담는다 — 착지 경로는 담지 않는다(재발급은 화면을 옮기지 않는다)")
    void returnsReissuedTokens() throws Exception {
        when(reissueTokenUseCase.reissue(any()))
                .thenReturn(new ReissueTokenUseCase.ReissuedTokens("eyJnewAccess", "eyJnewRefresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"eyJold"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("eyJnewAccess"))
                .andExpect(jsonPath("$.data.refreshToken").value("eyJnewRefresh"))
                .andExpect(jsonPath("$.data.landingPath").doesNotExist());
    }

    /*
     * 프론트는 아직 keepSignedIn 을 실어 보낸다. 그 키가 400 을 만들면 FE 배포 전까지 재발급이
     * 통째로 멈추므로, 무시하고 통과하는지 못박아 둔다(Jackson 기본값에 기대는 동작이라
     * 설정이 바뀌면 여기서 먼저 깨진다). 값은 서버가 표에서 읽은 것이 이긴다.
     */
    @Test
    @DisplayName("프론트가 keepSignedIn 을 계속 보내도 무시하고 200 — FE 배포를 기다리지 않아도 된다")
    void ignoresLegacyKeepSignedInField() throws Exception {
        when(reissueTokenUseCase.reissue(any()))
                .thenReturn(new ReissueTokenUseCase.ReissuedTokens("access", "refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"eyJold","keepSignedIn":true}
                                """))
                .andExpect(status().isOk());

        verify(reissueTokenUseCase).reissue("eyJold");
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

        verify(reissueTokenUseCase, org.mockito.Mockito.never()).reissue(any());
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

    @Test
    @DisplayName("어드민 겸직 팀장의 정보를 내려준다 — roleName 은 하위팀이 아니라 자유 라벨이다")
    void returnsMyProfile() throws Exception {
        authenticateAs(3L);
        when(getMyProfileUseCase.get(3L)).thenReturn(new MyProfile(
                3L, 1L, "(주)테크스타트", "NOVA-7K3D",
                "이하윤", "hayun@zgroup.co.kr", "010-1234-5678",
                1L, "개발팀", "프론트엔드", 4L, "선임",
                Authority.LEADER, true, true,
                MemberStatus.ACTIVE, LocalDate.of(2022, 5, 10), "TEAM", true));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(3))
                .andExpect(jsonPath("$.data.authority").value("LEADER"))
                .andExpect(jsonPath("$.data.isAdmin").value(true))
                .andExpect(jsonPath("$.data.roleName").value("프론트엔드"))
                .andExpect(jsonPath("$.data.workStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan").value("TEAM"))
                .andExpect(jsonPath("$.data.landingPath").value("/team"))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"));
    }

    @Test
    @DisplayName("과금이 쓴 요금제 코드를 그대로 내린다 — FREE·TEAM 이 아닌 값이어도 /me 는 200 이다")
    void returnsPlanCodeUnknownToThisDomain() throws Exception {
        /*
         * 회귀: plan 을 enum(FREE·TEAM)으로 다루던 시절 과금이 plan='STANDARD' 를 쓰기 시작하자
         * 결제한 회사의 /me 가 통째로 500 이 됐다. 값 목록의 주인은 과금 도메인이므로 여기서
         * 복제하지 않는다 — 계약은 "코드 문자열을 그대로 전달"이다. FREE·TEAM 만 검증하면
         * enum 만 처리하는 회귀가 그대로 통과하므로, 이 도메인이 모르는 코드로 못 박는다.
         */
        authenticateAs(5L);
        when(getMyProfileUseCase.get(5L)).thenReturn(new MyProfile(
                5L, 1L, "(주)결제완료", "H7QW-2M4X",
                "최결제", "paid@zgroup.co.kr", "010-2222-3333",
                1L, "개발팀", "프론트엔드", 4L, "선임",
                Authority.MEMBER, false, true,
                MemberStatus.ACTIVE, LocalDate.of(2026, 4, 4), "STANDARD"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("STANDARD"));
    }

    @Test
    @DisplayName("온보딩 전 오너는 부서·직급·라벨·구독이 전부 null 로 나가고 200 이다")
    void returnsNullFieldsForOwnerBeforeOnboarding() throws Exception {
        authenticateAs(9L);
        when(getMyProfileUseCase.get(9L)).thenReturn(new MyProfile(
                9L, 2L, "(주)신규", "ABCD-EFGH",
                "대표", "owner@new.kr", null,
                null, null, null, null, null,
                Authority.OWNER, false, false,
                MemberStatus.ACTIVE, null, null, false));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").doesNotExist())
                .andExpect(jsonPath("$.data.roleName").doesNotExist())
                .andExpect(jsonPath("$.data.plan").doesNotExist())
                .andExpect(jsonPath("$.data.isOnboarded").value(false))
                .andExpect(jsonPath("$.data.landingPath").value("/owner"));
    }

    @Test
    @DisplayName("프로필 수정은 바디가 아니라 토큰의 memberId·companyId 로 대상을 정한다")
    void updateMeUsesPrincipalNotBody() throws Exception {
        authenticateAs(3L);
        when(updateMyProfileUseCase.update(any())).thenReturn(new MyProfile(
                3L, 1L, "(주)테크스타트", "NOVA-7K3D",
                "이하윤", "hayun@zgroup.co.kr", "010-9999-0000",
                1L, "개발팀", "프론트엔드", 4L, "선임",
                Authority.MEMBER, false, true,
                MemberStatus.ACTIVE, LocalDate.of(2022, 5, 10), "FREE", true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"010-9999-0000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("010-9999-0000"));

        ArgumentCaptor<com.module06.backend.identity.member.application.command.UpdateMyProfileCommand> captor =
                ArgumentCaptor.forClass(com.module06.backend.identity.member.application.command.UpdateMyProfileCommand.class);
        verify(updateMyProfileUseCase).update(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(3L);
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().phone()).isEqualTo("010-9999-0000");
    }

    @Test
    @DisplayName("비밀번호 변경은 바디가 아니라 토큰의 memberId 로 대상을 정한다")
    void changePasswordUsesPrincipalNotBody() throws Exception {
        authenticateAs(3L);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Old1234!","newPassword":"NewPass12!",\
                                "newPasswordConfirm":"NewPass12!","memberId":99}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<ChangePasswordCommand> captor = ArgumentCaptor.forClass(ChangePasswordCommand.class);
        verify(changeMyPasswordUseCase).changePassword(captor.capture());
        // 바디에 memberId=99 를 실어 보내도 토큰의 3L 이 이긴다.
        assertThat(captor.getValue().memberId()).isEqualTo(3L);
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().newPassword()).isEqualTo("NewPass12!");
    }

    @Test
    @DisplayName("정책에 맞지 않는 새 비밀번호는 서비스까지 가지 않는다 — 400 에 입력값이 실리지 않는다")
    void rejectsPasswordFailingPolicyWithoutLeakingValue() throws Exception {
        authenticateAs(3L);

        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Old1234!","newPassword":"onlyletters",\
                                "newPasswordConfirm":"onlyletters"}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // 어느 칸이 틀렸는지는 알려주되, 입력한 값 자체는 응답 어디에도 없어야 한다.
        assertThat(response).contains("newPassword");
        assertThat(response).doesNotContain("onlyletters");
        verify(changeMyPasswordUseCase, org.mockito.Mockito.never()).changePassword(any());
    }

    @Test
    @DisplayName("요청 DTO 의 toString 은 비밀번호를 가린다 — 로그로 새는 유일한 경로다")
    void requestToStringMasksPasswords() {
        String printed = new com.module06.backend.identity.auth.presentation.api.dto.request.ChangeMyPasswordRequest(
                "Old1234!", "NewPass12!", "NewPass12!").toString();

        assertThat(printed).doesNotContain("Old1234!");
        assertThat(printed).doesNotContain("NewPass12!");
    }

    /** 필터를 끈 슬라이스 테스트라 컨텍스트를 직접 심는다 — JwtAuthenticationFilterTest 와 같은 방식. */
    private void authenticateAs(Long memberId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, 1L, "MEMBER", false, 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }
}
