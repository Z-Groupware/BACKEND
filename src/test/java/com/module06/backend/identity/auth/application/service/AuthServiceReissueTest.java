package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtProperties;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.auth.infrastructure.persistence.InMemoryRefreshTokenStore;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 재발급의 핵심은 두 가지다.
 *   1) 쓴 갱신표는 즉시 폐기된다(로테이션) — 남겨두면 탈취된 표가 수명 내내 통한다.
 *   2) 이미 쓴 표가 다시 오면 그 사람 표를 전부 폐기한다(재사용 탐지) — 탈취 정황이므로
 *      정상 사용자까지 끊는 편이 탈취자가 계속 갱신하는 것보다 낫다.
 *
 * 그리고 재발급 때 DB 를 다시 읽는 것이 의도다. 리프레시 토큰에는 memberId·jti 만 있어서
 * 새 액세스 토큰의 클레임을 채울 수 없고, 다시 읽으면 권한 변경이 30분을 기다리지 않고 반영된다.
 */
@DisplayName("AuthService 재발급·로그아웃")
class AuthServiceReissueTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Long MEMBER_ID = 3L;

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(new JwtProperties(
            SECRET, Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14)));
    private final RefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Test
    @DisplayName("갱신표를 새 토큰 쌍으로 교환한다")
    void reissuesTokenPair() {
        String token = issuedRefreshToken("jti-1");

        AuthService.ReissuedTokens result = service().reissue(token, false);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(token);
    }

    @Test
    @DisplayName("새 액세스 토큰이 지금의 DB 권한을 담는다 — 30분 지연 없이 갱신된다")
    void reissuedAccessTokenReflectsCurrentRole() {
        String token = issuedRefreshToken("jti-2");

        String access = service(member(Authority.LEADER, true)).reissue(token, false).accessToken();

        AuthPrincipal principal = tokenProvider.parseAccessToken(access);
        assertThat(principal.authority()).isEqualTo("LEADER");
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.memberId()).isEqualTo(MEMBER_ID);
        assertThat(principal.teamId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("쓴 갱신표는 즉시 폐기된다 — 로테이션")
    void rotatesOldToken() {
        String token = issuedRefreshToken("jti-3");

        service().reissue(token, false);

        assertThat(store.exists(MEMBER_ID, "jti-3")).isFalse();
    }

    @Test
    @DisplayName("새로 발급한 갱신표는 저장소에 올라간다 — 올리지 않으면 다음 재발급이 거부된다")
    void savesNewToken() {
        String token = issuedRefreshToken("jti-4");

        String reissued = service().reissue(token, false).refreshToken();

        assertThat(store.exists(MEMBER_ID, tokenProvider.parseRefreshToken(reissued).jti())).isTrue();
    }

    @Test
    @DisplayName("같은 갱신표를 두 번 쓰면 재사용으로 보고 그 사람 표를 전부 폐기한다")
    void detectsReuseAndRevokesEverything() {
        String token = issuedRefreshToken("jti-5");
        store.save(MEMBER_ID, "jti-other-device", Duration.ofDays(1));
        AuthService service = service();
        service.reissue(token, false);

        assertThatThrownBy(() -> service.reissue(token, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED);
        assertThat(store.exists(MEMBER_ID, "jti-other-device")).isFalse();
    }

    @Test
    @DisplayName("서명이 깨진 갱신표는 REFRESH_TOKEN_INVALID")
    void rejectsBrokenToken() {
        assertReissueFails("not-a-jwt", AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("액세스 토큰을 갱신표 자리에 넣으면 거부한다 — 액세스는 갱신표로 못 쓴다")
    void rejectsAccessTokenAsRefreshToken() {
        String access = tokenProvider.createAccessToken(
                new AuthPrincipal(MEMBER_ID, 1L, "MEMBER", false, 2L));

        assertReissueFails(access, AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("퇴사한 사람은 재발급을 못 받고 남은 표도 전부 폐기된다")
    void resignedMemberCannotReissue() {
        String token = issuedRefreshToken("jti-6");
        store.save(MEMBER_ID, "jti-other-device", Duration.ofDays(1));

        assertThatThrownBy(() -> service(resignedMember()).reissue(token, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_DELETED);
        assertThat(store.exists(MEMBER_ID, "jti-other-device")).isFalse();
    }

    @Test
    @DisplayName("사라진 구성원의 갱신표는 REFRESH_TOKEN_INVALID — MEMBER_NOT_FOUND 를 내리면 재로그인 유도가 안 된다")
    void unknownMemberIsInvalid() {
        String token = issuedRefreshToken("jti-7");

        assertThatThrownBy(() -> service(null).reissue(token, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("재발급에서도 로그인 유지가 갱신표 수명을 1일과 14일로 가른다")
    void keepSignedInChangesNewTokenTtl() {
        RecordingStore recording = new RecordingStore();
        String token = tokenProvider.createRefreshToken(MEMBER_ID, "jti-8", true);
        recording.save(MEMBER_ID, "jti-8", Duration.ofDays(14));

        service(member(Authority.MEMBER, false), recording).reissue(token, true);

        assertThat(recording.savedTtl).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("로그아웃은 그 사람의 갱신표를 전부 지운다 — 다른 기기도 함께 끊긴다")
    void logoutRevokesAllTokens() {
        store.save(MEMBER_ID, "jti-a", Duration.ofDays(1));
        store.save(MEMBER_ID, "jti-b", Duration.ofDays(1));

        service().logout(MEMBER_ID);

        assertThat(store.exists(MEMBER_ID, "jti-a")).isFalse();
        assertThat(store.exists(MEMBER_ID, "jti-b")).isFalse();
    }

    @Test
    @DisplayName("로그아웃은 남의 갱신표를 건드리지 않는다")
    void logoutLeavesOtherMembersAlone() {
        store.save(MEMBER_ID, "jti-mine", Duration.ofDays(1));
        store.save(99L, "jti-someone-else", Duration.ofDays(1));

        service().logout(MEMBER_ID);

        assertThat(store.exists(99L, "jti-someone-else")).isTrue();
    }

    private void assertReissueFails(String refreshToken, AuthErrorCode expected) {
        assertThatThrownBy(() -> service().reissue(refreshToken, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private String issuedRefreshToken(String jti) {
        store.save(MEMBER_ID, jti, Duration.ofDays(1));
        return tokenProvider.createRefreshToken(MEMBER_ID, jti, false);
    }

    private AuthService service() {
        return service(member(Authority.LEADER, true));
    }

    private AuthService service(MemberCredentials credentials) {
        return service(credentials, store);
    }

    /** {@code credentials} 가 null 이면 "그 구성원이 없다" 는 뜻이다. */
    private AuthService service(MemberCredentials credentials, RefreshTokenStore refreshTokenStore) {
        return new AuthService(noCompany(), port(credentials), refreshTokenStore,
                tokenProvider, new BCryptPasswordEncoder());
    }

    /** 재발급·로그아웃은 기업 조회를 쓰지 않는다. 쓰면 이 구현 때문에 테스트가 깨져서 드러난다. */
    private CompanyRepository noCompany() {
        return new CompanyRepository() {
            @Override
            public Optional<Company> findByCode(String code) {
                return Optional.empty();
            }

            @Override
            public Optional<Company> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public void lockForUpdate(Long companyId) {
            }
        };
    }

    private MemberAuthQueryPort port(MemberCredentials credentials) {
        return new MemberAuthQueryPort() {
            @Override
            public Optional<MemberCredentials> findForLogin(Long companyId, String email) {
                return Optional.empty();
            }

            @Override
            public Optional<MemberCredentials> findById(Long memberId) {
                return Optional.ofNullable(credentials);
            }
        };
    }

    private MemberCredentials member(Authority role, boolean isAdmin) {
        return new MemberCredentials(MEMBER_ID, 1L, "hash", role, isAdmin, 2L, false);
    }

    private MemberCredentials resignedMember() {
        return new MemberCredentials(MEMBER_ID, 1L, "hash", Authority.MEMBER, false, 2L, true);
    }

    private static final class RecordingStore implements RefreshTokenStore {
        private final RefreshTokenStore delegate = new InMemoryRefreshTokenStore();
        private Duration savedTtl;

        @Override
        public void save(Long memberId, String jti, Duration ttl) {
            this.savedTtl = ttl;
            delegate.save(memberId, jti, ttl);
        }

        @Override
        public boolean exists(Long memberId, String jti) {
            return delegate.exists(memberId, jti);
        }

        @Override
        public void revoke(Long memberId, String jti) {
            delegate.revoke(memberId, jti);
        }

        @Override
        public void revokeAllByMember(Long memberId) {
            delegate.revokeAllByMember(memberId);
        }
    }
}
