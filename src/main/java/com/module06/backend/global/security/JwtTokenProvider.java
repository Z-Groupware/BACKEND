package com.module06.backend.global.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import javax.crypto.SecretKey;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;



public class JwtTokenProvider {

    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_AUTHORITY = "authority";
    private static final String CLAIM_IS_ADMIN = "isAdmin";
    private static final String CLAIM_TEAM_ID = "teamId";


    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    /*
     * 갱신표에만 실리는 두 클레임. 둘 다 "로그인 시점의 사실"이라 재발급이 그대로 승계한다.
     * 서명 안에 있으므로 토큰을 들고 있는 쪽이 값을 바꿀 수 없다 — 서버가 따로 보관하지
     * 않아도 위조되지 않는 이유다.
     */
    private static final String CLAIM_AUTH_TIME = "authTime";
    private static final String CLAIM_KEEP_SIGNED_IN = "kis";

    private final JwtProperties properties;
    private final SecretKey key;


    private static final int MIN_SECRET_BYTES = 32;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(secretBytes(properties));
    }


    private static byte[] secretBytes(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 이 비어 있다. application-secret.yml 의 JWT_SECRET 을 확인하라. "
                            + "테스트라면 src/test/resources/application.yaml 의 jwt 블록을 확인하라 "
                            + "(그 파일이 main 의 application.yaml 을 가린다).");
        }
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "jwt.secret 이 hex 가 아니다. 길이가 짝수여야 하고 0-9a-f 만 쓸 수 있다. 생성: openssl rand -hex 32");
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 이 너무 짧다 — hex 를 디코드하면 %d바이트다. HS256 은 최소 %d바이트가 필요하다. "
                            .formatted(bytes.length, MIN_SECRET_BYTES)
                            + "생성: openssl rand -hex 32");
        }
        return bytes;
    }


    /**
     * @param keepSignedIn 로그인 시점의 "로그인 유지" 선택. 재발급 때 클라이언트에게 다시 묻지
     *                     않으려고 토큰이 들고 다닌다 — 다시 물으면 1일짜리 세션을 재발급 한 번으로
     *                     14일로 승급시킬 수 있다.
     * @param authTime     최초 로그인 시각. 재발급이 이 값을 승계하므로, 로테이션을 반복해도
     *                     세션의 절대 나이가 리셋되지 않는다.
     */
    public record RefreshClaims(Long memberId, String jti, boolean keepSignedIn, Instant authTime) {
    }


    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.memberId()))
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_COMPANY_ID, principal.companyId())
                .claim(CLAIM_AUTHORITY, principal.authority())
                .claim(CLAIM_IS_ADMIN, principal.isAdmin())
                .claim(CLAIM_TEAM_ID, principal.teamId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @param authTime 최초 로그인 시각. <b>재발급은 새로 찍지 말고 앞 표의 값을 넘겨야 한다</b> —
     *                 매번 새로 찍으면 절대 수명이 갱신마다 리셋되어 검사가 무력해진다.
     */
    public String createRefreshToken(Long memberId, String jti, boolean keepSignedIn, Instant authTime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .claim(CLAIM_KEEP_SIGNED_IN, keepSignedIn)
                .claim(CLAIM_AUTH_TIME, authTime.toEpochMilli())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl(keepSignedIn))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }


    public Duration refreshTtl(boolean keepSignedIn) {
        return keepSignedIn ? properties.refreshTtlExtended() : properties.refreshTtlDefault();
    }

    /**
     * 최초 로그인 이후 절대 상한을 넘겼는가.
     *
     * <p>표 한 장의 수명({@link #refreshTtl})은 재발급마다 새로 계산된다. 그래서 이 검사가 없으면
     * 만료 직전에 한 번씩 갱신하는 것만으로 세션이 영원히 살고, 탈취된 갱신표가 영구 세션이 된다.
     * 로테이션·재사용 탐지는 <b>정상 사용자가 그 표를 다시 써야</b> 작동하므로, 탈취자가 조용히
     * 혼자만 갱신하는 경우를 잡지 못한다 — 이 상한이 그 경우의 마지막 방어선이다.
     */
    public boolean refreshSessionExpired(Instant authTime) {
        return Duration.between(authTime, Instant.now()).compareTo(properties.refreshAbsoluteMax()) > 0;
    }

    public AuthPrincipal parseAccessToken(String token) {
        Claims claims = parse(token, AuthErrorCode.UNAUTHORIZED);
        requireType(claims, TYPE_ACCESS, AuthErrorCode.UNAUTHORIZED);

        Number companyId = claims.get(CLAIM_COMPANY_ID, Number.class);
        String authority = claims.get(CLAIM_AUTHORITY, String.class);
        if (companyId == null || authority == null) {

            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        return new AuthPrincipal(
                memberId(claims, AuthErrorCode.UNAUTHORIZED),
                companyId.longValue(),
                authority,
                Boolean.TRUE.equals(claims.get(CLAIM_IS_ADMIN, Boolean.class)),
                toNullableLong(claims.get(CLAIM_TEAM_ID, Number.class)));
    }

    public RefreshClaims parseRefreshToken(String token) {
        Claims claims = parse(token, AuthErrorCode.REFRESH_TOKEN_INVALID);
        requireType(claims, TYPE_REFRESH, AuthErrorCode.REFRESH_TOKEN_INVALID);

        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        // authTime 이 없는 표 = 이 클레임을 넣기 전에 발급된 것. 폴백을 두지 않고 재로그인시킨다 —
        // "없으면 지금 시각으로 친다" 같은 폴백은 그 자체가 우회 경로가 된다(절대 수명 검사를
        // 영원히 통과하는 표가 존재하게 된다). 배포 시점에 한 번 전원이 다시 로그인하면 끝난다.
        Number authTime = claims.get(CLAIM_AUTH_TIME, Number.class);
        if (authTime == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        return new RefreshClaims(
                memberId(claims, AuthErrorCode.REFRESH_TOKEN_INVALID),
                jti,
                Boolean.TRUE.equals(claims.get(CLAIM_KEEP_SIGNED_IN, Boolean.class)),
                Instant.ofEpochMilli(authTime.longValue()));
    }

    private Claims parse(String token, AuthErrorCode onFailure) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(onFailure);
        }
    }


    private void requireType(Claims claims, String expected, AuthErrorCode onFailure) {
        if (!expected.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new BusinessException(onFailure);
        }
    }

    private Long memberId(Claims claims, AuthErrorCode onFailure) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(onFailure);
        }
    }

    private Long toNullableLong(Number value) {
        return value == null ? null : value.longValue();
    }
}
