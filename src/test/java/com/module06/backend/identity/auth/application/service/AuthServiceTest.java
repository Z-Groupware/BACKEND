package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtProperties;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.dto.LoginResult;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 실패 응답이 갈리지 않는지가 이 테스트의 핵심이다. 회사 없음·이메일 없음·비밀번호 틀림이
 * 서로 다른 코드로 나가면 그것만으로 계정 존재 확인 도구가 된다.
 */
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String CODE = "8AS2-G8T1";
    private static final String EMAIL = "hayun@zgroup.co.kr";
    private static final String PASSWORD = "Abcd1234";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(new JwtProperties(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14)));

    @Test
    @DisplayName("로그인하면 토큰 두 개와 착지 경로를 준다")
    void issuesTokensAndLandingPath() {
        RecordingStore store = new RecordingStore();
        AuthService service = service(member(Authority.LEADER, false), store);

        LoginResult result = service.login(new LoginCommand(CODE, EMAIL, PASSWORD, false));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.landingPath()).isEqualTo("/team");
    }

    @Test
    @DisplayName("액세스 토큰에 역할·어드민 겸직·팀이 실린다 — 필터가 이 값으로 권한을 심는다")
    void accessTokenCarriesClaims() {
        AuthService service = service(member(Authority.MEMBER, true), new RecordingStore());

        LoginResult result = service.login(new LoginCommand(CODE, EMAIL, PASSWORD, false));
        AuthPrincipal principal = tokenProvider.parseAccessToken(result.accessToken());

        assertThat(principal.memberId()).isEqualTo(3L);
        assertThat(principal.companyId()).isEqualTo(1L);
        assertThat(principal.authority()).isEqualTo("MEMBER");
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.teamId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("어드민을 겸직해도 착지 경로는 역할 그대로다 — 팀장 겸 어드민은 /team")
    void adminDoesNotChangeLandingPath() {
        AuthService service = service(member(Authority.LEADER, true), new RecordingStore());

        assertThat(service.login(new LoginCommand(CODE, EMAIL, PASSWORD, false)).landingPath())
                .isEqualTo("/team");
    }

    @Test
    @DisplayName("발급한 리프레시 토큰을 갱신표에 올린다 — 올리지 않으면 재발급이 즉시 거부된다")
    void savesRefreshTokenToStore() {
        RecordingStore store = new RecordingStore();
        AuthService service = service(member(Authority.MEMBER, false), store);

        service.login(new LoginCommand(CODE, EMAIL, PASSWORD, false));

        assertThat(store.savedMemberId).isEqualTo(3L);
        assertThat(store.savedJti).isNotBlank();
    }

    @Test
    @DisplayName("로그인 유지를 켜면 리프레시 수명이 14일, 끄면 1일 — 액세스 수명은 바뀌지 않는다")
    void keepSignedInChangesOnlyRefreshTtl() {
        RecordingStore off = new RecordingStore();
        service(member(Authority.MEMBER, false), off).login(new LoginCommand(CODE, EMAIL, PASSWORD, false));

        RecordingStore on = new RecordingStore();
        service(member(Authority.MEMBER, false), on).login(new LoginCommand(CODE, EMAIL, PASSWORD, true));

        assertThat(off.savedTtl).isEqualTo(Duration.ofDays(1));
        assertThat(on.savedTtl).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("기업 코드를 대소문자·공백 섞어 보내도 정규화해서 찾는다")
    void normalizesCompanyCode() {
        RecordingRepository repository = new RecordingRepository(
                Optional.of(new Company(1L, CODE, "(주)테크스타트")));
        AuthService service = new AuthService(repository, port(member(Authority.MEMBER, false)),
                new RecordingStore(), tokenProvider, encoder);

        service.login(new LoginCommand("  8as2-g8t1 ", EMAIL, PASSWORD, false));

        assertThat(repository.requestedCode).isEqualTo(CODE);
    }

    @Test
    @DisplayName("없는 기업 코드는 LOGIN_FAILED — COMPANY_CODE_NOT_FOUND 로 내리면 어느 회사가 있는지 알려준다")
    void unknownCompanyIsLoginFailed() {
        AuthService service = new AuthService(new RecordingRepository(Optional.empty()),
                port(member(Authority.MEMBER, false)), new RecordingStore(), tokenProvider, encoder);

        assertLoginFailed(service, PASSWORD);
    }

    @Test
    @DisplayName("없는 이메일은 LOGIN_FAILED — 회사 없음과 같은 응답이어야 한다")
    void unknownEmailIsLoginFailed() {
        AuthService service = new AuthService(repository(), port(null),
                new RecordingStore(), tokenProvider, encoder);

        assertLoginFailed(service, PASSWORD);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LOGIN_FAILED — 앞의 둘과 같은 응답이어야 한다")
    void wrongPasswordIsLoginFailed() {
        AuthService service = service(member(Authority.MEMBER, false), new RecordingStore());

        assertLoginFailed(service, "WrongPass1");
    }

    @Test
    @DisplayName("퇴사자는 ACCOUNT_DELETED — 단, 비밀번호가 맞은 뒤에만 알려준다")
    void resignedMemberIsRejectedAfterPasswordCheck() {
        AuthService service = service(resignedMember(), new RecordingStore());

        assertThatThrownBy(() -> service.login(new LoginCommand(CODE, EMAIL, PASSWORD, false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_DELETED);
    }

    @Test
    @DisplayName("퇴사자라도 비밀번호가 틀리면 LOGIN_FAILED — ACCOUNT_DELETED 를 먼저 내리면 계정 존재가 새어 나간다")
    void resignedMemberWithWrongPasswordIsLoginFailed() {
        AuthService service = service(resignedMember(), new RecordingStore());

        assertLoginFailed(service, "WrongPass1");
    }

    @Test
    @DisplayName("없는 기업 코드에도 비밀번호를 검증한다 — 건너뛰면 응답 시간만으로 실패 이유가 갈린다")
    void verifiesPasswordEvenWhenCompanyIsAbsent() {
        CountingEncoder counting = new CountingEncoder();
        AuthService service = new AuthService(new RecordingRepository(Optional.empty()),
                port(member(Authority.MEMBER, false)), new RecordingStore(), tokenProvider, counting);

        assertLoginFailed(service, PASSWORD);

        assertThat(counting.matchCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 이메일에도 비밀번호를 검증한다 — 기업 코드를 아는 사람이 이메일을 열거할 수 있다")
    void verifiesPasswordEvenWhenEmailIsAbsent() {
        CountingEncoder counting = new CountingEncoder();
        AuthService service = new AuthService(repository(), port(null),
                new RecordingStore(), tokenProvider, counting);

        assertLoginFailed(service, PASSWORD);

        assertThat(counting.matchCalls).isEqualTo(1);
    }

    /*
     * 호출 횟수만 세면 부족하다. 더미로 아무 문자열을 넘기면 BCryptPasswordEncoder 는 형식을 보고
     * 즉시 false 를 반환해서(경고만 찍는다) 시간이 전혀 맞지 않는데, 횟수 검사만으로는 통과한다.
     */
    @Test
    @DisplayName("구성원이 없을 때 넘기는 더미도 진짜 BCrypt 해시다 — 가짜 문자열이면 즉시 반환돼 시간이 안 맞는다")
    void absentMemberHashIsRealBcrypt() {
        CountingEncoder counting = new CountingEncoder();
        AuthService service = new AuthService(new RecordingRepository(Optional.empty()),
                port(member(Authority.MEMBER, false)), new RecordingStore(), tokenProvider, counting);

        assertLoginFailed(service, PASSWORD);

        assertThat(counting.lastEncodedPassword)
                .as("BCrypt 해시 형식이어야 검증 비용이 실제 해시와 같아진다")
                .matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    @Test
    @DisplayName("비밀번호가 맞아도 검증은 한 번만 한다 — 성공 경로가 느려지면 그것도 신호가 된다")
    void verifiesPasswordExactlyOnceOnSuccess() {
        CountingEncoder counting = new CountingEncoder();
        AuthService service = new AuthService(repository(), port(memberWith(counting)),
                new RecordingStore(), tokenProvider, counting);

        service.login(new LoginCommand(CODE, EMAIL, PASSWORD, false));

        assertThat(counting.matchCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인 실패는 갱신표에 아무것도 올리지 않는다")
    void failedLoginSavesNothing() {
        RecordingStore store = new RecordingStore();
        AuthService service = service(member(Authority.MEMBER, false), store);

        assertLoginFailed(service, "WrongPass1");

        assertThat(store.savedJti).isNull();
    }

    private void assertLoginFailed(AuthService service, String password) {
        assertThatThrownBy(() -> service.login(new LoginCommand(CODE, EMAIL, password, false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    private AuthService service(MemberCredentials credentials, RefreshTokenStore store) {
        return new AuthService(repository(), port(credentials), store, tokenProvider, encoder);
    }

    private CompanyRepository repository() {
        return new RecordingRepository(Optional.of(new Company(1L, CODE, "(주)테크스타트")));
    }

    /** {@code credentials} 가 null 이면 "그 구성원이 없다" 는 뜻이다. */
    private MemberAuthQueryPort port(MemberCredentials credentials) {
        return new MemberAuthQueryPort() {
            @Override
            public Optional<MemberCredentials> findForLogin(Long companyId, String email) {
                return EMAIL.equals(email) ? Optional.ofNullable(credentials) : Optional.empty();
            }

            @Override
            public Optional<MemberCredentials> findById(Long memberId) {
                return Optional.ofNullable(credentials);
            }
        };
    }

    private MemberCredentials member(Authority role, boolean isAdmin) {
        return new MemberCredentials(3L, 1L, encoder.encode(PASSWORD), role, isAdmin, 2L, false);
    }

    /** 해시를 세는 인코더로 만들어야 matchCalls 가 setUp 의 encode 까지 세지 않는다. */
    private MemberCredentials memberWith(PasswordEncoder with) {
        return new MemberCredentials(3L, 1L, with.encode(PASSWORD), Authority.MEMBER, false, 2L, false);
    }

    private MemberCredentials resignedMember() {
        return new MemberCredentials(3L, 1L, encoder.encode(PASSWORD), Authority.MEMBER, false, 2L, true);
    }

    /** 실제 BCrypt 를 그대로 쓰면서 matches 호출만 기록한다 — 검증 비용을 흉내내지 않는다. */
    private static final class CountingEncoder implements PasswordEncoder {
        private final PasswordEncoder delegate = new BCryptPasswordEncoder();
        private int matchCalls;
        private String lastEncodedPassword;

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchCalls++;
            lastEncodedPassword = encodedPassword;
            return delegate.matches(rawPassword, encodedPassword);
        }
    }

    private static final class RecordingRepository implements CompanyRepository {
        private final Optional<Company> result;
        private String requestedCode;

        private RecordingRepository(Optional<Company> result) {
            this.result = result;
        }

        @Override
        public Optional<Company> findByCode(String code) {
            this.requestedCode = code;
            return result;
        }
    }

    private static final class RecordingStore implements RefreshTokenStore {
        private Long savedMemberId;
        private String savedJti;
        private Duration savedTtl;

        @Override
        public void save(Long memberId, String jti, Duration ttl) {
            this.savedMemberId = memberId;
            this.savedJti = jti;
            this.savedTtl = ttl;
        }

        @Override
        public boolean exists(Long memberId, String jti) {
            return jti.equals(savedJti);
        }

        @Override
        public void revoke(Long memberId, String jti) {
            savedJti = null;
        }

        @Override
        public void revokeAllByMember(Long memberId) {
            savedJti = null;
        }
    }
}
