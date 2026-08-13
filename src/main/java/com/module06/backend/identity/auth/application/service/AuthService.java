package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.dto.LoginResult;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;

/**
 * 로그인 · 토큰 재발급 · 로그아웃.
 *
 * <p>실패 응답을 하나로 모은다 — 회사 없음·이메일 없음·비밀번호 틀림이 모두 {@code LOGIN_FAILED} 다.
 * 구분해서 내리면 그것만으로 "이 회사가 이 서비스를 쓴다", "이 이메일이 이 회사에 있다"를 확인하는
 * 도구가 된다.
 *
 * <p><b>응답 코드만 통일해서는 부족하다.</b> 회사·이메일이 없을 때 즉시 반환하면 BCrypt 검증
 * 한 번(수십 ms)만큼 응답이 빨라져서, 시간을 반복 측정하는 것만으로 같은 정보가 새어 나간다.
 * 그래서 구성원을 못 찾아도 더미 해시에 대해 검증을 <b>반드시 한 번</b> 수행한다.
 *
 * <p>세 흐름이 한 클래스에 있는 이유는 갱신표({@link RefreshTokenStore})를 공유하기 때문이다.
 * 로그인이 표를 올리고, 재발급이 교체하고, 로그아웃이 지운다 — 나누면 그 규칙이 세 곳에 흩어진다.
 */
@Service
public class AuthService implements LoginUseCase, ReissueTokenUseCase, LogoutUseCase {

    private final CompanyRepository companyRepository;
    private final MemberAuthQueryPort memberAuthQueryPort;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * 구성원을 못 찾았을 때 검증 대상으로 쓰는 해시. 리터럴을 박지 않고 주입된 인코더로 만든다 —
     * BCrypt 의 검증 비용은 해시 문자열에 박힌 cost 에서 나오므로, DB 의 해시를 만든 것과 같은
     * 인코더가 만들어야 비용이 일치한다. 형식이 어긋나면 {@code matches} 가 즉시 false 를 반환해
     * (경고만 남는다) 시간을 맞추려는 목적 자체가 무너진다.
     */
    private final String absentMemberHash;

    public AuthService(CompanyRepository companyRepository,
                       MemberAuthQueryPort memberAuthQueryPort,
                       RefreshTokenStore refreshTokenStore,
                       JwtTokenProvider tokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.memberAuthQueryPort = memberAuthQueryPort;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.absentMemberHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginCommand command) {
        MemberCredentials member = companyRepository.findByCode(normalize(command.companyCode()))
                .flatMap(company -> memberAuthQueryPort.findForLogin(company.id(), command.email()))
                .orElse(null);

        // 어느 경로로 실패하든 검증을 정확히 한 번 수행한다 — 분기마다 횟수가 달라지면 시간도 달라진다.
        boolean passwordMatches = passwordEncoder.matches(
                command.password(),
                member == null ? absentMemberHash : member.passwordHash());

        if (member == null || !passwordMatches) {
            throw loginFailed();
        }

        // 비밀번호가 맞은 뒤에 본다. 먼저 보면 "퇴사자가 있는 계정"임을 비밀번호 없이 확인할 수 있다.
        if (member.resigned()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DELETED);
        }

        return issueTokens(member, command.keepSignedIn());
    }

    @Override
    @Transactional(readOnly = true)
    public ReissuedTokens reissue(String refreshToken) {
        JwtTokenProvider.RefreshClaims claims = tokenProvider.parseRefreshToken(refreshToken);

        // 로테이션으로 이어붙인 세션도 최초 로그인 시각 기준으로는 끝난다.
        // 이 표만 지운다(revokeAll 이 아니다) — 다른 기기의 세션은 각자의 authTime 으로 판정받는다.
        // 코드는 REFRESH_TOKEN_INVALID 를 쓴다: 프론트 대응이 "재로그인"으로 같고, 따로 내리면
        // 공격자에게 "서명은 맞았고 세션만 늙었다"를 알려주게 된다(AuthErrorCode 머리말과 같은 이유).
        if (tokenProvider.refreshSessionExpired(claims.authTime())) {
            refreshTokenStore.revoke(claims.memberId(), claims.jti());
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (!refreshTokenStore.exists(claims.memberId(), claims.jti())) {
            // 서명은 유효한데 목록에 없다 = 이미 쓴 표가 다시 왔다 = 탈취 정황이다.
            // 정상 사용자도 함께 끊기지만, 탈취자가 계속 갱신하는 것보다 낫다.
            refreshTokenStore.revokeAllByMember(claims.memberId());
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSED);
        }

        // 클레임을 채우기 위해 지금의 권한을 다시 읽는다 — 권한 변경이 30분을 기다리지 않고 반영된다.
        MemberCredentials member = memberAuthQueryPort.findById(claims.memberId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (member.resigned()) {
            refreshTokenStore.revokeAllByMember(claims.memberId());
            throw new BusinessException(AuthErrorCode.ACCOUNT_DELETED);
        }

        refreshTokenStore.revoke(claims.memberId(), claims.jti());

        // keepSignedIn·authTime 둘 다 앞 표에서 승계한다. 요청 바디에서 받지 않는 이유는
        // 승급(1일 → 14일)과 절대 수명 리셋을 클라이언트가 할 수 있게 되기 때문이다.
        String jti = UUID.randomUUID().toString();
        refreshTokenStore.save(claims.memberId(), jti, tokenProvider.refreshTtl(claims.keepSignedIn()));

        return new ReissuedTokens(
                tokenProvider.createAccessToken(principalOf(member)),
                tokenProvider.createRefreshToken(
                        claims.memberId(), jti, claims.keepSignedIn(), claims.authTime()));
    }

    @Override
    public void logout(Long memberId) {
        refreshTokenStore.revokeAllByMember(memberId);
    }

    private LoginResult issueTokens(MemberCredentials member, boolean keepSignedIn) {
        String accessToken = tokenProvider.createAccessToken(principalOf(member));

        String jti = UUID.randomUUID().toString();
        // 여기서만 authTime 을 새로 찍는다. 재발급은 이 값을 승계한다 — 그래야 절대 수명이
        // 세션 전체를 재는 값이 된다.
        String refreshToken = tokenProvider.createRefreshToken(
                member.memberId(), jti, keepSignedIn, Instant.now());
        Duration ttl = tokenProvider.refreshTtl(keepSignedIn);

        // 갱신표에 올리지 않으면 방금 발급한 토큰으로 재발급이 거부된다.
        refreshTokenStore.save(member.memberId(), jti, ttl);

        return new LoginResult(accessToken, refreshToken, member.authority().landingPath());
    }

    /** 로그인과 재발급이 같은 클레임을 실어야 한다 — 갈리면 재발급 뒤에 권한이 조용히 달라진다. */
    private AuthPrincipal principalOf(MemberCredentials member) {
        return new AuthPrincipal(member.memberId(), member.companyId(),
                member.authority().name(), member.isAdmin(), member.teamId());
    }

    /** 메일에서 복사하면 앞뒤 공백이 붙고 대소문자도 섞여 들어온다(API 01 과 같은 규칙). */
    private String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }

    private static BusinessException loginFailed() {
        return new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }
}
