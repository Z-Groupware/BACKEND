package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
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
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 · 토큰 재발급 · 로그아웃.
 *
 * <p>실패 응답을 하나로 모은다 — 회사 없음·이메일 없음·비밀번호 틀림이 모두 {@code LOGIN_FAILED} 다.
 * 구분해서 내리면 그것만으로 "이 회사가 이 서비스를 쓴다", "이 이메일이 이 회사에 있다"를 확인하는
 * 도구가 된다.
 *
 * <p>세 흐름이 한 클래스에 있는 이유는 갱신표({@link RefreshTokenStore})를 공유하기 때문이다.
 * 로그인이 표를 올리고, 재발급이 교체하고, 로그아웃이 지운다 — 나누면 그 규칙이 세 곳에 흩어진다.
 */
@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase, ReissueTokenUseCase, LogoutUseCase {

    private final CompanyRepository companyRepository;
    private final MemberAuthQueryPort memberAuthQueryPort;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginCommand command) {
        Company company = companyRepository.findByCode(normalize(command.companyCode()))
                .orElseThrow(AuthService::loginFailed);

        MemberCredentials member = memberAuthQueryPort.findForLogin(company.id(), command.email())
                .orElseThrow(AuthService::loginFailed);

        if (!passwordEncoder.matches(command.password(), member.passwordHash())) {
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
    public ReissuedTokens reissue(String refreshToken, boolean keepSignedIn) {
        JwtTokenProvider.RefreshClaims claims = tokenProvider.parseRefreshToken(refreshToken);

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

        String jti = UUID.randomUUID().toString();
        refreshTokenStore.save(claims.memberId(), jti, tokenProvider.refreshTtl(keepSignedIn));

        return new ReissuedTokens(
                tokenProvider.createAccessToken(principalOf(member)),
                tokenProvider.createRefreshToken(claims.memberId(), jti, keepSignedIn));
    }

    @Override
    public void logout(Long memberId) {
        refreshTokenStore.revokeAllByMember(memberId);
    }

    private LoginResult issueTokens(MemberCredentials member, boolean keepSignedIn) {
        String accessToken = tokenProvider.createAccessToken(principalOf(member));

        String jti = UUID.randomUUID().toString();
        String refreshToken = tokenProvider.createRefreshToken(member.memberId(), jti, keepSignedIn);
        Duration ttl = tokenProvider.refreshTtl(keepSignedIn);

        // 갱신표에 올리지 않으면 방금 발급한 토큰으로 재발급이 거부된다.
        refreshTokenStore.save(member.memberId(), jti, ttl);

        return new LoginResult(accessToken, refreshToken, member.role().landingPath());
    }

    /** 로그인과 재발급이 같은 클레임을 실어야 한다 — 갈리면 재발급 뒤에 권한이 조용히 달라진다. */
    private AuthPrincipal principalOf(MemberCredentials member) {
        return new AuthPrincipal(member.memberId(), member.companyId(),
                member.role().name(), member.isAdmin(), member.teamId());
    }

    /** 메일에서 복사하면 앞뒤 공백이 붙고 대소문자도 섞여 들어온다(API 01 과 같은 규칙). */
    private String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }

    private static BusinessException loginFailed() {
        return new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }
}
