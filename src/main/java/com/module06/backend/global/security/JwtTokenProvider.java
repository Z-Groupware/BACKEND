package com.module06.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;


// @Component 를 쓰지 않는다 — SecurityConfig 가 @Bean 으로 등록한다.
// @WebMvcTest 는 컴포넌트 스캔을 하지 않으면서 SecurityConfig 는 로드하므로,
// 스캔에 의존하면 팀원들의 컨트롤러 슬라이스 테스트가 빈 부족으로 깨진다.
public class JwtTokenProvider {

    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_IS_ADMIN = "isAdmin";
    private static final String CLAIM_TEAM_ID = "teamId";

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
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 이 너무 짧다 — %d바이트. HS256 은 최소 %d바이트가 필요하다. 생성: openssl rand -hex 32"
                            .formatted(bytes.length, MIN_SECRET_BYTES));
        }
        return bytes;
    }


    public record RefreshClaims(Long memberId, String jti) {
    }

    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.memberId()))
                .claim(CLAIM_COMPANY_ID, principal.companyId())
                .claim(CLAIM_ROLE, principal.role())
                .claim(CLAIM_IS_ADMIN, principal.isAdmin())
                .claim(CLAIM_TEAM_ID, principal.teamId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long memberId, String jti, boolean keepSignedIn) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl(keepSignedIn))))
                .signWith(key)
                .compact();
    }


    public Duration refreshTtl(boolean keepSignedIn) {
        return keepSignedIn ? properties.refreshTtlExtended() : properties.refreshTtlDefault();
    }

    public AuthPrincipal parseAccessToken(String token) {
        Claims claims = parse(token, AuthErrorCode.UNAUTHORIZED);
        return new AuthPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_COMPANY_ID, Number.class).longValue(),
                claims.get(CLAIM_ROLE, String.class),
                Boolean.TRUE.equals(claims.get(CLAIM_IS_ADMIN, Boolean.class)),
                toNullableLong(claims.get(CLAIM_TEAM_ID, Number.class)));
    }

    public RefreshClaims parseRefreshToken(String token) {
        Claims claims = parse(token, AuthErrorCode.REFRESH_TOKEN_INVALID);
        return new RefreshClaims(Long.valueOf(claims.getSubject()), claims.getId());
    }

    private Claims parse(String token, AuthErrorCode onFailure) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(onFailure);
        }
    }

    private Long toNullableLong(Number value) {
        return value == null ? null : value.longValue();
    }
}
