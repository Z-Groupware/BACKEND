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
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;

/**
 * 로그인 2단계.
 *
 * <p>실패 응답을 하나로 모은다 — 회사 없음·이메일 없음·비밀번호 틀림이 모두 {@code LOGIN_FAILED} 다.
 * 구분해서 내리면 그것만으로 "이 회사가 이 서비스를 쓴다", "이 이메일이 이 회사에 있다"를 확인하는
 * 도구가 된다.
 *
 * <p><b>응답 코드만 통일해서는 부족하다.</b> 회사·이메일이 없을 때 즉시 반환하면 BCrypt 검증
 * 한 번(수십 ms)만큼 응답이 빨라져서, 시간을 반복 측정하는 것만으로 같은 정보가 새어 나간다.
 * 그래서 구성원을 못 찾아도 더미 해시에 대해 검증을 <b>반드시 한 번</b> 수행한다.
 */
@Service
public class AuthService implements LoginUseCase {

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
