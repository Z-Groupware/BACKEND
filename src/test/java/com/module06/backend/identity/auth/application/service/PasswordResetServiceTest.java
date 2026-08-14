package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.ratelimit.InMemoryRateLimiter;
import com.module06.backend.global.ratelimit.RateLimitProperties;
import com.module06.backend.global.ratelimit.RateLimiter;
import com.module06.backend.identity.auth.application.command.ResetPasswordCommand;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.auth.domain.policy.PasswordPolicy;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 비밀번호 찾기(POST /api/auth/password/reset).
 *
 * 이 테스트가 지키는 것은 세 가지다.
 *   1) 메일이 나간 것을 확인한 뒤에만 저장한다 — 반대면 발송 실패 시 사용자가 새 비밀번호를
 *      모르는 채로 계정이 잠기고, 관리자 재발급 경로도 없어 복구가 불가능하다.
 *   2) 메일에 실린 평문으로 실제 로그인이 된다 — 저장한 해시와 보낸 값이 어긋나면 사용자는
 *      맞는 비밀번호를 받고도 못 들어온다.
 *   3) 성공도 횟수 제한에 걸린다 — 이 API 는 성공하는 순간 남의 로그인이 막히므로 성공이 공격이다.
 */
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    private static final String CODE = "8AS2-G8T1";
    private static final String EMAIL = "hayun@zgroup.co.kr";
    private static final Long MEMBER_ID = 3L;
    private static final Long COMPANY_ID = 1L;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final RateLimiter rateLimiter = new InMemoryRateLimiter();
    private final RateLimitProperties rateLimitProperties = new RateLimitProperties(
            new RateLimitProperties.Rule(60, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(5)),
            new RateLimitProperties.Rule(120, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(20, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(5)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(3, Duration.ofHours(24)));

    private final RecordingPasswordPort passwordPort =
            new RecordingPasswordPort(new BCryptPasswordEncoder().encode("Old1234!"));
    private final RecordingMail mail = new RecordingMail();
    private final RecordingLogout logout = new RecordingLogout();

    @Test
    @DisplayName("새 비밀번호를 기업 코드와 함께 메일로 보내고, 저장하고, 모든 기기를 끊는다")
    void sendsSavesAndRevokes() {
        service().resetPassword(new ResetPasswordCommand(CODE, EMAIL));

        assertThat(mail.sentTo).isEqualTo(EMAIL);
        assertThat(mail.sentCompanyCode).isEqualTo(CODE);
        assertThat(passwordPort.changeCount()).isEqualTo(1);
        assertThat(logout.loggedOut).isEqualTo(MEMBER_ID);
    }

    /*
     * 메일 속 평문과 DB 의 해시가 어긋나면 사용자는 맞는 비밀번호를 받고도 로그인하지 못하고,
     * 옛 비밀번호는 이미 못 쓰는 상태라 계정이 잠긴다.
     */
    @Test
    @DisplayName("메일로 보낸 그 비밀번호가 저장된 해시와 맞는다")
    void mailedPasswordMatchesStoredHash() {
        service().resetPassword(new ResetPasswordCommand(CODE, EMAIL));

        assertThat(encoder.matches(mail.sentPassword, passwordPort.currentHash())).isTrue();
    }

    @Test
    @DisplayName("보낸 비밀번호는 비밀번호 정책을 지킨다 — 받자마자 마이페이지에서 바꿔도 규칙에 걸리지 않는다")
    void mailedPasswordSatisfiesPolicy() {
        service().resetPassword(new ResetPasswordCommand(CODE, EMAIL));

        assertThat(PasswordPolicy.isSatisfiedBy(mail.sentPassword)).isTrue();
    }

    /*
     * 이 테스트가 "메일 먼저, 저장 나중" 순서의 존재 이유다. 순서가 반대면 여기서 비밀번호가
     * 이미 바뀌어 있고, 사용자는 새 값을 모른 채 로그인할 방법을 잃는다.
     */
    @Test
    @DisplayName("메일 발송이 실패하면 비밀번호를 바꾸지 않는다 — 기존 비밀번호가 살아 있다")
    void keepsOldPasswordWhenMailFails() {
        mail.shouldFail = true;
        String hashBefore = passwordPort.currentHash();

        assertThatThrownBy(() -> service().resetPassword(new ResetPasswordCommand(CODE, EMAIL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_MAIL_FAILED);

        assertThat(passwordPort.currentHash()).isEqualTo(hashBefore);
        assertThat(passwordPort.changeCount()).isZero();
        assertThat(logout.loggedOut).isNull();
    }

    @Test
    @DisplayName("없는 기업 코드는 404 — 메일도 나가지 않는다")
    void rejectsUnknownCompany() {
        assertResetFails(new ResetPasswordCommand("NOPE-0000", EMAIL));
        assertThat(mail.sentTo).isNull();
    }

    @Test
    @DisplayName("그 회사에 없는 이메일은 404")
    void rejectsUnknownEmail() {
        assertResetFails(new ResetPasswordCommand(CODE, "nobody@zgroup.co.kr"));
    }

    /*
     * 403(ACCOUNT_DELETED)이 아니라 404 다. 구분해서 답하면 "그 사람 퇴사했다"를 로그인도 없이
     * 확인할 수 있는 도구가 된다.
     */
    @Test
    @DisplayName("퇴사한 계정은 없는 계정과 똑같이 404")
    void rejectsResignedMemberSameAsMissing() {
        PasswordResetService service = service(resignedMember());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordCommand(CODE, EMAIL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_ACCOUNT_NOT_FOUND);
        assertThat(mail.sentTo).isNull();
    }

    /** 로그인과 정반대다 — 여기는 성공이 곧 "남의 로그인을 막는" 행위라 성공도 세야 한다. */
    @Test
    @DisplayName("성공한 재발급도 횟수 제한에 쌓인다 — 하루 3번이면 막힌다")
    void countsSuccessTowardLimit() {
        PasswordResetService service = service();
        for (int i = 0; i < 3; i++) {
            service.resetPassword(new ResetPasswordCommand(CODE, EMAIL));
        }

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordCommand(CODE, EMAIL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("없는 계정으로 두들기는 것도 센다 — 응답이 계정 목록을 훑는 도구가 되지 않는다")
    void countsMissesTowardLimit() {
        PasswordResetService service = service();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.resetPassword(new ResetPasswordCommand(CODE, "nobody@zgroup.co.kr")));
        }

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordCommand(CODE, "nobody@zgroup.co.kr")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("기업 코드는 대소문자·공백을 섞어 보내도 찾는다 — 로그인과 같은 규칙")
    void normalizesCompanyCode() {
        service().resetPassword(new ResetPasswordCommand("  8as2-g8t1 ", EMAIL));

        // 메일에는 사용자가 친 값이 아니라 저장된 정본이 실린다.
        assertThat(mail.sentCompanyCode).isEqualTo(CODE);
    }

    private void assertResetFails(ResetPasswordCommand command) {
        assertThatThrownBy(() -> service().resetPassword(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_ACCOUNT_NOT_FOUND);
    }

    private PasswordResetService service() {
        return service(activeMember());
    }

    private PasswordResetService service(MemberCredentials credentials) {
        return new PasswordResetService(
                companyRepository(), port(credentials), passwordPort, mail,
                new PasswordGenerator(new Random(20260814L)), encoder,
                rateLimiter, rateLimitProperties, logout);
    }

    private MemberCredentials activeMember() {
        return new MemberCredentials(MEMBER_ID, COMPANY_ID, passwordPort.currentHash(),
                Authority.MEMBER, false, 2L, false);
    }

    private MemberCredentials resignedMember() {
        return new MemberCredentials(MEMBER_ID, COMPANY_ID, passwordPort.currentHash(),
                Authority.MEMBER, false, 2L, true);
    }

    private CompanyRepository companyRepository() {
        return new CompanyRepository() {
            @Override
            public Optional<Company> findByCode(String code) {
                return CODE.equals(code)
                        ? Optional.of(new Company(COMPANY_ID, CODE, "(주)테크스타트", null, null, null, null, null, null, null))
                        : Optional.empty();
            }

            @Override
            public Optional<Company> findById(Long id) {
                throw new AssertionError("비밀번호 찾기는 회사를 id 로 조회하지 않는다");
            }

            @Override
            public void lockForUpdate(Long companyId) {
                throw new AssertionError("비밀번호 찾기는 회사를 잠그지 않는다");
            }
        };
    }

    private MemberAuthQueryPort port(MemberCredentials credentials) {
        return new MemberAuthQueryPort() {
            @Override
            public Optional<MemberCredentials> findForLogin(Long companyId, String email) {
                return EMAIL.equals(email) ? Optional.of(credentials) : Optional.empty();
            }

            @Override
            public Optional<MemberCredentials> findById(Long memberId) {
                throw new AssertionError("비밀번호 찾기는 id 로 구성원을 조회하지 않는다");
            }
        };
    }

    private static final class RecordingMail implements AccountMailPort {
        private String sentTo;
        private String sentCompanyCode;
        private String sentPassword;
        private boolean shouldFail;

        @Override
        public void sendAccountIssued(String toEmail, String companyCode, String password) {
            throw new UnsupportedOperationException("비밀번호 찾기는 계정 발급 메일을 쓰지 않는다");
        }

        @Override
        public boolean sendPasswordReset(String toEmail, String companyCode, String password) {
            if (shouldFail) {
                return false;
            }
            this.sentTo = toEmail;
            this.sentCompanyCode = companyCode;
            this.sentPassword = password;
            return true;
        }
    }

    /** 갱신표 폐기는 AuthService 한 곳에 남겨 두고 여기서는 위임만 한다 — 그 위임이 일어나는지 본다. */
    private static final class RecordingLogout implements LogoutUseCase {
        private final List<Long> calls = new ArrayList<>();
        private Long loggedOut;

        @Override
        public void logout(Long memberId) {
            calls.add(memberId);
            loggedOut = memberId;
        }
    }
}
