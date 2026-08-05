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
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 2단계.
 *
 * <p>실패 응답을 하나로 모은다 — 회사 없음·이메일 없음·비밀번호 틀림이 모두 {@code LOGIN_FAILED} 다.
 * 구분해서 내리면 그것만으로 "이 회사가 이 서비스를 쓴다", "이 이메일이 이 회사에 있다"를 확인하는
 * 도구가 된다.
 */
@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase {

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

    private LoginResult issueTokens(MemberCredentials member, boolean keepSignedIn) {
        String accessToken = tokenProvider.createAccessToken(new AuthPrincipal(
                member.memberId(), member.companyId(), member.role().name(),
                member.isAdmin(), member.teamId()));

        String jti = UUID.randomUUID().toString();
        String refreshToken = tokenProvider.createRefreshToken(member.memberId(), jti, keepSignedIn);
        Duration ttl = tokenProvider.refreshTtl(keepSignedIn);

        // 갱신표에 올리지 않으면 방금 발급한 토큰으로 재발급이 거부된다.
        refreshTokenStore.save(member.memberId(), jti, ttl);

        return new LoginResult(accessToken, refreshToken, member.role().landingPath());
    }

    /** 메일에서 복사하면 앞뒤 공백이 붙고 대소문자도 섞여 들어온다(API 01 과 같은 규칙). */
    private String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }

    private static BusinessException loginFailed() {
        return new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }
}
