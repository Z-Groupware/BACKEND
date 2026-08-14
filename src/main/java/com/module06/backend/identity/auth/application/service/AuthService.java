package com.module06.backend.identity.auth.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.audit.AuthzAuditLogger;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.ratelimit.RateLimitPolicy;
import com.module06.backend.global.ratelimit.RateLimitProperties;
import com.module06.backend.global.ratelimit.RateLimitSubject;
import com.module06.backend.global.ratelimit.RateLimiter;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.identity.auth.application.command.ChangePasswordCommand;
import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.dto.LoginResult;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.application.usecase.ChangeMyPasswordUseCase;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;
import com.module06.backend.identity.member.application.port.out.MemberPasswordPort;

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
 * <p>네 흐름이 한 클래스에 있는 이유는 갱신표({@link RefreshTokenStore})를 공유하기 때문이다.
 * 로그인이 표를 올리고, 재발급이 교체하고, 로그아웃과 비밀번호 변경이 지운다 — 나누면 그 규칙이
 * 여러 곳에 흩어진다.
 */
@Service
public class AuthService implements LoginUseCase, ReissueTokenUseCase, LogoutUseCase, ChangeMyPasswordUseCase {

    private final CompanyRepository companyRepository;
    private final MemberAuthQueryPort memberAuthQueryPort;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    /** 비밀번호 변경에서만 쓴다. 로그인·재발급은 읽기 창구({@link MemberAuthQueryPort})로 충분하다. */
    private final MemberPasswordPort memberPasswordPort;

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
                       PasswordEncoder passwordEncoder,
                       RateLimiter rateLimiter,
                       RateLimitProperties rateLimitProperties,
                       MemberPasswordPort memberPasswordPort) {
        this.companyRepository = companyRepository;
        this.memberAuthQueryPort = memberAuthQueryPort;
        this.memberPasswordPort = memberPasswordPort;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.absentMemberHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /*
     * 계정 기준 제한은 여기서 본다 — 필터는 요청 본문을 읽을 수 없어 어느 계정을 노렸는지 모른다.
     * IP 기준 제한(RateLimitFilter)만으로는 부족하다: 공격자는 IP 를 바꿔 가며 한 계정을 팰 수 있고,
     * 반대로 사무실 하나에서 직원 수십 명이 같은 IP 로 로그인한다. 두 축이 각자 다른 것을 막는다.
     *
     * 세는 것은 실패뿐이다. 모든 시도를 세면 잘 쓰는 사용자가 자기 로그인으로 잠긴다.
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginCommand command) {
        String accountKey = RateLimitSubject.ofAccount(command.companyCode(), command.email());
        RateLimitPolicy accountPolicy = rateLimitProperties.loginAccountPolicy();

        // 시작에서는 보기만 한다. 여기서 계상하면 성공한 로그인도 카운터를 밀어 올린다.
        if (!rateLimiter.peek(accountPolicy, accountKey).allowed()) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_REQUESTS);
        }

        MemberCredentials member = companyRepository.findByCode(normalize(command.companyCode()))
                .flatMap(company -> memberAuthQueryPort.findForLogin(company.id(), command.email()))
                .orElse(null);

        // 어느 경로로 실패하든 검증을 정확히 한 번 수행한다 — 분기마다 횟수가 달라지면 시간도 달라진다.
        boolean passwordMatches = passwordEncoder.matches(
                command.password(),
                member == null ? absentMemberHash : member.passwordHash());

        if (member == null || !passwordMatches) {
            // 실패한 뒤에 올린다. 회사 없음·이메일 없음·비번 틀림이 모두 여기로 모이므로,
            // 존재하지 않는 계정을 훑는 시도도 같은 카운터에 쌓인다 — 계정 존재 여부로
            // 제한을 우회할 수 없다.
            rateLimiter.record(accountPolicy, accountKey);
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
            // 여기서 남긴다 — 예외 핸들러는 이 표가 누구 것이었는지 모른다(재발급은 공개
            // 엔드포인트라 인증 주체가 없다). 끊는 것과 알리는 것은 다른 일이고, 알림이 없으면
            // 이 코드가 주석에 적어 둔 "탈취 정황"이 발생해도 아무도 모른다.
            AuthzAuditLogger.refreshTokenReused(claims.memberId(), AuthErrorCode.REFRESH_TOKEN_REUSED.getCode());
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

    /*
     * 이 메서드에 @Transactional 을 붙이지 않는 것은 의도다. 붙이면 마지막 줄의 갱신표 폐기가
     * 커밋 전에 실행되어, 저장이 롤백돼도 세션만 끊긴 상태가 남는다. 트랜잭션 경계는
     * MemberPasswordPort 구현이 자기 안에서 가지므로, 그 호출이 끝나면 이미 커밋된 뒤다.
     *
     * 순서 자체가 규칙이다 — 폐기를 먼저 하면 비밀번호가 안 바뀐 채 세션만 끊기고, 저장 뒤에
     * 폐기하면 최악의 경우 "바꿨는데 옛 세션이 잠깐 남는" 상태가 된다. 후자가 사용자에게
     * 덜 나쁘고, 폐기 실패는 로그로 드러난다.
     *
     * 로그인과 달리 못 찾은 구성원용 더미 해시를 태우지 않는다. 여기는 이미 본인이 인증된
     * 자리라 계정 존재 여부가 새어 나갈 수 없고, 타이밍을 맞출 대상 자체가 없다.
     */
    @Override
    public void changePassword(ChangePasswordCommand command) {
        String subject = RateLimitSubject.ofMember(command.memberId());
        RateLimitPolicy policy = rateLimitProperties.passwordChangePolicy();

        // 로그인과 같은 규칙 — 시작에서는 보기만 하고, 현재 비밀번호가 틀렸을 때만 계상한다.
        if (!rateLimiter.peek(policy, subject).allowed()) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_REQUESTS);
        }

        // 확인칸부터 본다. 여기서 걸리면 아무것도 조회할 필요가 없고, 오타 하나로 제한 카운터가
        // 올라가지도 않는다.
        if (!command.newPassword().equals(command.newPasswordConfirm())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        MemberCredentials member = memberAuthQueryPort.findById(command.memberId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(command.currentPassword(), member.passwordHash())) {
            rateLimiter.record(policy, subject);
            throw new BusinessException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        // 지금 쓰는 값과 예전에 쓰던 값을 나눠 답한다 — 사용자가 다음에 무엇을 넣을지가 달라진다.
        if (passwordEncoder.matches(command.newPassword(), member.passwordHash())) {
            throw new BusinessException(AuthErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }
        if (usedBefore(command.memberId(), command.newPassword())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_ALREADY_USED);
        }

        memberPasswordPort.changePassword(command.memberId(), passwordEncoder.encode(command.newPassword()));

        // 비밀번호를 바꾸는 이유의 절반은 "샜을지도 모른다" 다. 모든 기기를 끊는다.
        refreshTokenStore.revokeAllByMember(command.memberId());
    }

    /**
     * BCrypt 해시는 같은 평문이라도 매번 다르다 — 문자열 비교로는 판정할 수 없어 하나씩 대조한다.
     * 이력이 쌓일수록 이 반복이 그대로 응답 시간이 된다({@code MemberPasswordPort} javadoc).
     */
    private boolean usedBefore(Long memberId, String newPassword) {
        return memberPasswordPort.findUsedPasswordHashes(memberId).stream()
                .anyMatch(usedHash -> passwordEncoder.matches(newPassword, usedHash));
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
