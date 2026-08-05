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


// @Component 를 쓰지 않는다 — SecurityConfig 가 @Bean 으로 등록한다.
// @WebMvcTest 는 컴포넌트 스캔을 하지 않으면서 SecurityConfig 는 로드하므로,
// 스캔에 의존하면 팀원들의 컨트롤러 슬라이스 테스트가 빈 부족으로 깨진다.
public class JwtTokenProvider {

    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_IS_ADMIN = "isAdmin";
    private static final String CLAIM_TEAM_ID = "teamId";

    // 토큰 종류를 클레임에 박는다. 없으면 리프레시 토큰을 Authorization 헤더로 보내도
    // 서명·만료가 맞아 파싱을 통과하고, 없는 companyId 를 꺼내다 NPE 가 나 401 대신 500 이 된다.
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

    /*
     * secret 은 hex 문자열이다(openssl rand -hex 32 → 64자). 디코드한 raw 바이트를 키로 쓴다.
     *
     * ASCII 바이트를 그대로 키로 넘기면 두 가지가 어긋난다.
     *  1) 64자를 64바이트로 보고 jjwt 가 알고리즘을 HS512 로 올려버린다 — 문서·주석은 HS256 인데.
     *  2) 32자 hex(엔트로피 16바이트)가 "32바이트" 로 계산돼 길이 검사를 통과한다.
     * 그래서 디코드 후의 길이를 잰다.
     */
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

    // 알고리즘을 HS256 으로 고정한다. 생략하면 jjwt 가 키 길이를 보고 고르므로,
    // 키를 더 긴 것으로 바꾸는 순간 알고리즘이 조용히 따라 바뀐다.
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
            // 서명은 우리 키인데 스키마가 다르다 — 구버전 토큰이거나 우리가 발급하지 않은 형태다.
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
            // jti 가 없으면 갱신표에서 찾을 키가 없다. 통과시키면 폐기 검사를 건너뛴다.
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

    /* 액세스 자리에 리프레시를(또는 그 반대로) 넣는 교차 사용을 막는다. */
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
