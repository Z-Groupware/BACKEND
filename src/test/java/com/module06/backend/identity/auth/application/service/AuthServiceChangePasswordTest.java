package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.ratelimit.InMemoryRateLimiter;
import com.module06.backend.global.ratelimit.RateLimitProperties;
import com.module06.backend.global.ratelimit.RateLimiter;
import com.module06.backend.global.security.JwtProperties;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.identity.auth.application.command.ChangePasswordCommand;
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
 * 마이페이지 비밀번호 변경(PATCH /api/auth/me/password).
 *
 * 이 테스트가 지키는 것은 두 가지다. 하나는 "현재 비밀번호를 모르면 절대 못 바꾼다" 이고,
 * 다른 하나는 "바꾸면 모든 기기가 끊긴다" 이다. 둘 중 하나라도 무너지면 액세스 토큰 하나를
 * 훔친 사람이 계정을 통째로 가져갈 수 있다.
 */
@DisplayName("AuthService 비밀번호 변경")
class AuthServiceChangePasswordTest {

    private static final Long MEMBER_ID = 3L;
    private static final Long COMPANY_ID = 1L;
    private static final String CURRENT = "Old1234!";
    private static final String NEW = "NewPass12!";

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
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(new JwtProperties(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30)));

    private final RecordingPasswordPort passwordPort = new RecordingPasswordPort(new BCryptPasswordEncoder().encode(CURRENT));
    private final RevokeRecordingStore store = new RevokeRecordingStore();

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 해시로 바뀌고, 모든 기기의 갱신표가 폐기된다")
    void changesPasswordAndRevokesEverySession() {
        service().changePassword(command(CURRENT, NEW, NEW));

        assertThat(encoder.matches(NEW, passwordPort.currentHash())).isTrue();
        assertThat(passwordPort.currentHash()).isNotEqualTo(NEW); // 평문이 저장되지 않는다
        assertThat(store.revokedAllFor).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 거절한다 — 저장도 세션 폐기도 일어나지 않는다")
    void rejectsWrongCurrentPassword() {
        assertThatThrownBy(() -> service().changePassword(command("WrongPass1!", NEW, NEW)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);

        assertThat(passwordPort.changeCount()).isZero();
        assertThat(store.revokedAllFor).isNull();
    }

    @Test
    @DisplayName("새 비밀번호와 확인값이 다르면 거절한다")
    void rejectsConfirmMismatch() {
        assertThatThrownBy(() -> service().changePassword(command(CURRENT, NEW, "NewPass12?")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);

        assertThat(passwordPort.changeCount()).isZero();
    }

    @Test
    @DisplayName("지금 쓰는 비밀번호로는 바꿀 수 없다")
    void rejectsSameAsCurrent() {
        assertThatThrownBy(() -> service().changePassword(command(CURRENT, CURRENT, CURRENT)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    /*
     * 이 테스트가 이력 테이블의 존재 이유다. 현재 해시 하나만 비교하면 두 번째 변경에서
     * 처음 값으로 되돌아갈 수 있고, 그러면 메일로 나간 발급 비밀번호가 영원히 되살아난다.
     */
    @Test
    @DisplayName("두 번 바꾼 뒤 처음 비밀번호로 되돌릴 수 없다 — 발급받은 비밀번호도 마찬가지다")
    void rejectsPasswordUsedBefore() {
        AuthService service = service();
        service.changePassword(command(CURRENT, NEW, NEW));
        service.changePassword(command(NEW, "Third456!", "Third456!"));

        assertThatThrownBy(() -> service.changePassword(command("Third456!", CURRENT, CURRENT)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_ALREADY_USED);
    }

    @Test
    @DisplayName("현재 비밀번호를 반복해 틀리면 막힌다 — 토큰을 훔쳐도 원래 비밀번호를 캐낼 수 없다")
    void blocksBruteForceOnCurrentPassword() {
        AuthService service = service();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.changePassword(command("WrongPass1!", NEW, NEW)));
        }

        assertThatThrownBy(() -> service.changePassword(command(CURRENT, NEW, NEW)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.TOO_MANY_REQUESTS);
    }

    /** 실패만 센다 — 성공한 변경이 카운터를 밀어 올리면 잘 쓰는 사용자가 자기 요청으로 잠긴다. */
    @Test
    @DisplayName("성공한 변경은 횟수 제한 카운터를 올리지 않는다")
    void successDoesNotCountTowardLimit() {
        AuthService service = service();
        service.changePassword(command(CURRENT, NEW, NEW));
        service.changePassword(command(NEW, "Third456!", "Third456!"));
        service.changePassword(command("Third456!", "Fourth78!", "Fourth78!"));
        service.changePassword(command("Fourth78!", "Fifth901!", "Fifth901!"));
        service.changePassword(command("Fifth901!", "Sixth234!", "Sixth234!"));

        assertThat(passwordPort.changeCount()).isEqualTo(5);
    }

    private ChangePasswordCommand command(String current, String newPassword, String confirm) {
        return new ChangePasswordCommand(MEMBER_ID, COMPANY_ID, current, newPassword, confirm);
    }

    private AuthService service() {
        return new AuthService(noCompany(), port(), store, tokenProvider, encoder,
                rateLimiter, rateLimitProperties, passwordPort);
    }

    /**
     * 현재 해시를 저장소 대역에서 읽는다. 서비스가 바꾼 값을 다음 호출에서 그대로 보게 하려면
     * 두 대역이 같은 값을 봐야 한다 — 고정된 해시를 돌려주면 "두 번 바꾸기" 테스트가 성립하지 않는다.
     */
    private MemberAuthQueryPort port() {
        return new MemberAuthQueryPort() {
            @Override
            public Optional<MemberCredentials> findForLogin(Long companyId, String email) {
                return Optional.empty();
            }

            @Override
            public Optional<MemberCredentials> findById(Long memberId) {
                return Optional.of(new MemberCredentials(
                        MEMBER_ID, COMPANY_ID, passwordPort.currentHash(), Authority.MEMBER, false, 2L, false));
            }
        };
    }

    /** 비밀번호 변경은 기업 조회를 쓰지 않는다. 쓰면 이 구현 때문에 테스트가 깨져서 드러난다. */
    private CompanyRepository noCompany() {
        return new CompanyRepository() {
            @Override
            public Optional<Company> findByCode(String code) {
                throw new AssertionError("비밀번호 변경은 기업을 조회하지 않는다");
            }

            @Override
            public Optional<Company> findById(Long id) {
                throw new AssertionError("비밀번호 변경은 기업을 조회하지 않는다");
            }

            @Override
            public void lockForUpdate(Long companyId) {
                throw new AssertionError("비밀번호 변경은 기업을 잠그지 않는다");
            }
        };
    }

    private static final class RevokeRecordingStore implements RefreshTokenStore {
        private Long revokedAllFor;

        @Override
        public void save(Long memberId, String jti, Duration ttl) {
        }

        @Override
        public boolean exists(Long memberId, String jti) {
            return true;
        }

        @Override
        public void revoke(Long memberId, String jti) {
        }

        @Override
        public void revokeAllByMember(Long memberId) {
            this.revokedAllFor = memberId;
        }
    }
}
