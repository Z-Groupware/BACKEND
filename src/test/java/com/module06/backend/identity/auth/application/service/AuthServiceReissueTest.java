package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.module06.backend.global.audit.AuthzAuditLogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.ratelimit.InMemoryRateLimiter;
import com.module06.backend.global.ratelimit.RateLimitProperties;
import com.module06.backend.global.ratelimit.RateLimiter;
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
import static org.assertj.core.api.Assertions.within;

/*
 * 재발급의 핵심은 네 가지다.
 *   1) 쓴 갱신표는 즉시 폐기된다(로테이션) — 남겨두면 탈취된 표가 수명 내내 통한다.
 *   2) 이미 쓴 표가 다시 오면 그 사람 표를 전부 폐기한다(재사용 탐지) — 탈취 정황이므로
 *      정상 사용자까지 끊는 편이 탈취자가 계속 갱신하는 것보다 낫다.
 *   3) 로그인 시점의 선택(keepSignedIn)을 표에서 읽는다 — 요청 바디에서 받으면 1일짜리
 *      세션을 재발급 한 번으로 14일로 승급시킬 수 있다.
 *   4) 최초 로그인 시각(authTime)을 승계해 절대 수명을 잰다 — 없으면 만료 직전에 한 번씩
 *      갱신하는 것만으로 세션이 영원히 살고, 1·2번은 정상 사용자가 그 표를 다시 써야만
 *      작동하므로 탈취자가 혼자 조용히 갱신하는 경우를 잡지 못한다.
 *
 * 그리고 재발급 때 DB 를 다시 읽는 것이 의도다. 표에 든 것만으로는 새 액세스 토큰의 권한
 * 클레임을 채울 수 없고, 다시 읽으면 권한 변경이 30분을 기다리지 않고 반영된다.
 */
@DisplayName("AuthService 재발급·로그아웃")
class AuthServiceReissueTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Long MEMBER_ID = 3L;
    private static final Duration ABSOLUTE_MAX = Duration.ofDays(30);

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(new JwtProperties(
            SECRET, Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), ABSOLUTE_MAX));
    private final RefreshTokenStore store = new InMemoryRefreshTokenStore();
    private final RateLimiter rateLimiter = new InMemoryRateLimiter();
    /** 재발급·로그아웃은 이 창구를 건드리지 않는다 — 건드리면 이 대역이 기록해서 드러난다. */
    private final RecordingPasswordPort passwordPort = new RecordingPasswordPort();
    private final RateLimitProperties rateLimitProperties = new RateLimitProperties(
            new RateLimitProperties.Rule(60, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(5)),
            new RateLimitProperties.Rule(120, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(20, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(1)),
            new RateLimitProperties.Rule(5, Duration.ofMinutes(5)));

    @Test
    @DisplayName("갱신표를 새 토큰 쌍으로 교환한다")
    void reissuesTokenPair() {
        String token = issuedRefreshToken("jti-1");

        AuthService.ReissuedTokens result = service().reissue(token);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(token);
    }

    @Test
    @DisplayName("새 액세스 토큰이 지금의 DB 권한을 담는다 — 30분 지연 없이 갱신된다")
    void reissuedAccessTokenReflectsCurrentRole() {
        String token = issuedRefreshToken("jti-2");

        String access = service(member(Authority.LEADER, true)).reissue(token).accessToken();

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

        service().reissue(token);

        assertThat(store.exists(MEMBER_ID, "jti-3")).isFalse();
    }

    @Test
    @DisplayName("새로 발급한 갱신표는 저장소에 올라간다 — 올리지 않으면 다음 재발급이 거부된다")
    void savesNewToken() {
        String token = issuedRefreshToken("jti-4");

        String reissued = service().reissue(token).refreshToken();

        assertThat(store.exists(MEMBER_ID, tokenProvider.parseRefreshToken(reissued).jti())).isTrue();
    }

    @Test
    @DisplayName("같은 갱신표를 두 번 쓰면 재사용으로 보고 그 사람 표를 전부 폐기한다")
    void detectsReuseAndRevokesEverything() {
        String token = issuedRefreshToken("jti-5");
        store.save(MEMBER_ID, "jti-other-device", Duration.ofDays(1));
        AuthService service = service();
        service.reissue(token);

        assertThatThrownBy(() -> service.reissue(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED);
        assertThat(store.exists(MEMBER_ID, "jti-other-device")).isFalse();
    }

    /*
     * 끊는 것과 알리는 것은 다른 일이다. 위 테스트는 표가 전부 폐기되는 것까지만 보는데,
     * 폐기만 하고 기록을 안 남기면 "탈취 정황"이 발생해도 아무도 모른다(P1 #5 가 지적한 자리다).
     * 여기서는 감사 로그가 실제로 나가는지, 그리고 누구의 표였는지가 담기는지를 본다.
     */
    @Test
    @DisplayName("재사용을 탐지하면 감사 로그를 ERROR 로 남긴다 — 폐기만 하고 알리지 않으면 아무도 모른다")
    void recordsAuditLogOnReuse() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuthzAuditLogger.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            String token = issuedRefreshToken("jti-audit");
            AuthService service = service();
            service.reissue(token);

            assertThatThrownBy(() -> service.reissue(token)).isInstanceOf(BusinessException.class);

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("outcome=TOKEN_REUSED")
                    .contains("actor=" + MEMBER_ID)
                    .contains("code=AU-005");
        } finally {
            auditLogger.detachAppender(appender);
        }
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

        assertThatThrownBy(() -> service(resignedMember()).reissue(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_DELETED);
        assertThat(store.exists(MEMBER_ID, "jti-other-device")).isFalse();
    }

    @Test
    @DisplayName("사라진 구성원의 갱신표는 REFRESH_TOKEN_INVALID — MEMBER_NOT_FOUND 를 내리면 재로그인 유도가 안 된다")
    void unknownMemberIsInvalid() {
        String token = issuedRefreshToken("jti-7");

        assertThatThrownBy(() -> service(null).reissue(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("로그인 유지로 받은 표는 재발급 뒤에도 14일이다 — 선택이 표를 타고 승계된다")
    void keepSignedInIsCarriedFromTheToken() {
        RecordingStore recording = new RecordingStore();
        String token = tokenProvider.createRefreshToken(MEMBER_ID, "jti-8", true, Instant.now());
        recording.save(MEMBER_ID, "jti-8", Duration.ofDays(14));

        service(member(Authority.MEMBER, false), recording).reissue(token);

        assertThat(recording.savedTtl).isEqualTo(Duration.ofDays(14));
    }

    /*
     * 이 검사가 막는 것: 예전에는 재발급 요청 바디에 keepSignedIn 을 실어 보낼 수 있었고,
     * 그래서 "로그인 유지"를 끄고 받은 1일짜리 세션을 재발급 한 번으로 14일로 늘릴 수 있었다.
     * 지금은 요청 바디에 그 필드가 없고, 값은 표의 kis 클레임(서명 안)에서만 온다.
     */
    @Test
    @DisplayName("로그인 유지 없이 받은 표는 재발급해도 1일 그대로다 — 14일로 승급되지 않는다")
    void shortSessionCannotBePromotedByReissuing() {
        RecordingStore recording = new RecordingStore();
        String token = tokenProvider.createRefreshToken(MEMBER_ID, "jti-9", false, Instant.now());
        recording.save(MEMBER_ID, "jti-9", Duration.ofDays(1));

        service(member(Authority.MEMBER, false), recording).reissue(token);

        assertThat(recording.savedTtl).isEqualTo(Duration.ofDays(1));
    }

    @Test
    @DisplayName("최초 로그인 시각은 재발급에서 승계된다 — 갱신마다 새로 찍으면 절대 수명이 무력해진다")
    void authTimeSurvivesRotation() {
        Instant loggedInAt = Instant.now().minus(Duration.ofDays(20));
        String token = tokenProvider.createRefreshToken(MEMBER_ID, "jti-10", false, loggedInAt);
        store.save(MEMBER_ID, "jti-10", Duration.ofDays(1));

        String reissued = service().reissue(token).refreshToken();

        assertThat(tokenProvider.parseRefreshToken(reissued).authTime())
                .isCloseTo(loggedInAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("절대 수명을 넘긴 세션은 표가 살아 있어도 재발급을 거부하고 그 표를 지운다")
    void rejectsSessionPastAbsoluteMax() {
        Instant tooOld = Instant.now().minus(ABSOLUTE_MAX).minus(Duration.ofDays(1));
        String token = tokenProvider.createRefreshToken(MEMBER_ID, "jti-11", true, tooOld);
        store.save(MEMBER_ID, "jti-11", Duration.ofDays(14));
        store.save(MEMBER_ID, "jti-other-device", Duration.ofDays(1));

        assertReissueFails(token, AuthErrorCode.REFRESH_TOKEN_INVALID);

        assertThat(store.exists(MEMBER_ID, "jti-11")).isFalse();
        // 이 세션만 끝난다 — 다른 기기는 각자의 authTime 으로 판정받아야 하므로 건드리지 않는다.
        assertThat(store.exists(MEMBER_ID, "jti-other-device")).isTrue();
    }

    /*
     * authTime 이 없는 표 = 이 클레임을 넣기 전에 발급된 것. 폴백("없으면 지금 시각으로 친다")을
     * 두면 그 표는 절대 수명 검사를 영원히 통과하므로, 폴백 없이 재로그인시킨다.
     */
    @Test
    @DisplayName("authTime 이 없는 옛 표는 거부한다 — 폴백을 두면 그게 우회 경로가 된다")
    void rejectsLegacyTokenWithoutAuthTime() {
        String legacy = Jwts.builder()
                .subject(String.valueOf(MEMBER_ID))
                .claim("tokenType", "refresh")
                .id("jti-legacy")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofDays(14))))
                .signWith(Keys.hmacShaKeyFor(HexFormat.of().parseHex(SECRET)), Jwts.SIG.HS256)
                .compact();
        store.save(MEMBER_ID, "jti-legacy", Duration.ofDays(14));

        assertReissueFails(legacy, AuthErrorCode.REFRESH_TOKEN_INVALID);
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
        assertThatThrownBy(() -> service().reissue(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private String issuedRefreshToken(String jti) {
        store.save(MEMBER_ID, jti, Duration.ofDays(1));
        return tokenProvider.createRefreshToken(MEMBER_ID, jti, false, Instant.now());
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
                tokenProvider, new BCryptPasswordEncoder(), rateLimiter, rateLimitProperties, passwordPort);
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
