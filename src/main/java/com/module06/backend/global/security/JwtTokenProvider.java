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
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_IS_ADMIN = "isAdmin";
    private static final String CLAIM_TEAM_ID = "teamId";


    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

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


    public record RefreshClaims(Long memberId, String jti) {
    }


    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.memberId()))
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_COMPANY_ID, principal.companyId())
                .claim(CLAIM_ROLE, principal.role())
                .claim(CLAIM_IS_ADMIN, principal.isAdmin())
                .claim(CLAIM_TEAM_ID, principal.teamId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(Long memberId, String jti, boolean keepSignedIn) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl(keepSignedIn))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }


    public Duration refreshTtl(boolean keepSignedIn) {
        return keepSignedIn ? properties.refreshTtlExtended() : properties.refreshTtlDefault();
    }

    public AuthPrincipal parseAccessToken(String token) {
        Claims claims = parse(token, AuthErrorCode.UNAUTHORIZED);
        requireType(claims, TYPE_ACCESS, AuthErrorCode.UNAUTHORIZED);

        Number companyId = claims.get(CLAIM_COMPANY_ID, Number.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        if (companyId == null || role == null) {

            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        return new AuthPrincipal(
                memberId(claims, AuthErrorCode.UNAUTHORIZED),
                companyId.longValue(),
                role,
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
        return new RefreshClaims(memberId(claims, AuthErrorCode.REFRESH_TOKEN_INVALID), jti);
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
