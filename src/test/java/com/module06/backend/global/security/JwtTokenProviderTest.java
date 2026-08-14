package com.module06.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30)));
    }

    @Test
    @DisplayName("액세스 토큰을 발급하고 파싱하면 클레임 5개가 그대로 보존된다")
    void accessTokenRoundTrip() {
        AuthPrincipal issued = new AuthPrincipal(3L, 1L, "LEADER", true, 2L);

        AuthPrincipal parsed = provider.parseAccessToken(provider.createAccessToken(issued));

        assertThat(parsed).isEqualTo(issued);
    }

    @Test
    @DisplayName("teamId 가 null 인 주체도 왕복에서 null 로 보존된다 — 온보딩 전 오너")
    void accessTokenKeepsNullTeamId() {
        AuthPrincipal owner = new AuthPrincipal(1L, 1L, "OWNER", false, null);

        AuthPrincipal parsed = provider.parseAccessToken(provider.createAccessToken(owner));

        assertThat(parsed.teamId()).isNull();
        assertThat(parsed).isEqualTo(owner);
    }

    @Test
    @DisplayName("리프레시 토큰은 memberId·jti·로그인유지·최초로그인시각을 실어 왕복한다")
    void refreshTokenRoundTrip() {
        Instant authTime = Instant.now().minus(Duration.ofDays(3));
        String token = provider.createRefreshToken(3L, "jti-abc", true, authTime);

        JwtTokenProvider.RefreshClaims claims = provider.parseRefreshToken(token);

        assertThat(claims.memberId()).isEqualTo(3L);
        assertThat(claims.jti()).isEqualTo("jti-abc");
        assertThat(claims.keepSignedIn()).isTrue();
        assertThat(claims.authTime()).isCloseTo(authTime, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("절대 수명은 최초 로그인 시각으로 잰다 — 상한을 넘기면 만료로 본다")
    void refreshSessionExpiresAtAbsoluteMax() {
        assertThat(provider.refreshSessionExpired(Instant.now().minus(Duration.ofDays(29)))).isFalse();
        assertThat(provider.refreshSessionExpired(Instant.now().minus(Duration.ofDays(31)))).isTrue();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거부한다")
    void rejectsForeignSignature() {
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30)));
        String foreign = other.createAccessToken(new AuthPrincipal(3L, 1L, "MEMBER", false, 2L));

        assertThatThrownBy(() -> provider.parseAccessToken(foreign))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectsExpiredToken() {
        JwtTokenProvider expiring = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30)));
        String expired = expiring.createAccessToken(new AuthPrincipal(3L, 1L, "MEMBER", false, 2L));

        assertThatThrownBy(() -> provider.parseAccessToken(expired))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("keepSignedIn 이 리프레시 유효기간을 1일과 14일로 가른다")
    void refreshTtlDependsOnKeepSignedIn() {
        assertThat(provider.refreshTtl(false)).isEqualTo(Duration.ofDays(1));
        assertThat(provider.refreshTtl(true)).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("64자 hex 키로 발급한 토큰의 alg 는 HS256 이다 — 키 길이에 따라 조용히 바뀌지 않는다")
    void signsWithHs256() {
        String jwt = provider.createAccessToken(new AuthPrincipal(3L, 1L, "MEMBER", false, 2L));

        String header = new String(
                Base64.getUrlDecoder().decode(jwt.substring(0, jwt.indexOf('.'))), StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("32자 hex 는 디코드하면 16바이트뿐이라 거부한다 — 길이를 ASCII 가 아니라 디코드 후로 잰다")
    void rejectsHexShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("0123456789abcdef0123456789abcdef", Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16바이트");
    }

    @Test
    @DisplayName("hex 가 아닌 secret 은 거부한다")
    void rejectsNonHexSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("not-a-hex-value-but-definitely-long-enough-to-pass-a-length-check",
                Duration.ofMinutes(30), Duration.ofDays(1), Duration.ofDays(14), Duration.ofDays(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hex");
    }

    @Test
    @DisplayName("리프레시 토큰을 액세스 자리에 넣으면 401 로 거부한다 — 예전엔 NPE 로 500 이 났다")
    void rejectsRefreshTokenUsedAsAccessToken() {
        String refresh = provider.createRefreshToken(3L, "jti-abc", false, Instant.now());

        assertThatThrownBy(() -> provider.parseAccessToken(refresh))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시 자리에 넣으면 거부한다 — 예전엔 jti=null 로 통과했다")
    void rejectsAccessTokenUsedAsRefreshToken() {
        String access = provider.createAccessToken(new AuthPrincipal(3L, 1L, "MEMBER", false, 2L));

        assertThatThrownBy(() -> provider.parseRefreshToken(access))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }
}
