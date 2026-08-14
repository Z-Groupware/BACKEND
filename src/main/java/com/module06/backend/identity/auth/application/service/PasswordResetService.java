package com.module06.backend.identity.auth.application.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.ratelimit.RateLimitPolicy;
import com.module06.backend.global.ratelimit.RateLimitProperties;
import com.module06.backend.global.ratelimit.RateLimitSubject;
import com.module06.backend.global.ratelimit.RateLimiter;
import com.module06.backend.identity.auth.application.command.ResetPasswordCommand;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ResetPasswordUseCase;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;
import com.module06.backend.identity.member.application.port.out.MemberPasswordPort;

import lombok.RequiredArgsConstructor;

/**
 * 비밀번호 찾기 — 잃어버린 비밀번호를 서버가 새로 만들어 메일로 보낸다.
 *
 * <p>{@link AuthService} 에 넣지 않고 따로 두는 이유는 두 가지다. 이 흐름만 쓰는 의존성이 둘
 * 있고({@link PasswordGenerator}·{@link AccountMailPort}), 여기에만 있는 순서 규칙이 있다
 * ("메일이 나간 것을 확인한 뒤에 저장한다"). 다만 갱신표를 직접 만지지는 않는다 —
 * {@link LogoutUseCase} 를 불러 폐기를 {@code AuthService} 한 곳에 남겨 둔다.
 *
 * <h2>왜 계정 존재를 숨기지 않는가</h2>
 *
 * <p>로그인({@code LOGIN_FAILED})과 정반대로, 없는 계정에는 404 로 답한다. 기업 코드를 함께 받으므로
 * 유효한 기업 코드를 이미 알아야 여기까지 올 수 있고, 기업 코드 조회에는 분당 20회 제한이 걸려 있다.
 * 오타를 친 사용자가 "메일을 보냈습니다"만 보고 오지 않는 메일을 기다리는 쪽이 더 나쁘다고 봤다.
 *
 * <h2>제한을 성공에도 거는 이유</h2>
 *
 * <p>로그인은 <b>실패만</b> 센다. 여기는 <b>성공도</b> 센다 — 이 API 는 성공하는 순간 남의 비밀번호가
 * 실제로 바뀌어 그 사람이 로그인하지 못하게 되므로, 성공 자체가 공격 수단이다. 세지 않는 것은
 * 메일 발송 실패뿐이다(사용자 잘못이 아니고, 그때는 아무것도 바뀌지 않는다).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService implements ResetPasswordUseCase {

    private final CompanyRepository companyRepository;
    private final MemberAuthQueryPort memberAuthQueryPort;
    private final MemberPasswordPort memberPasswordPort;
    private final AccountMailPort accountMailPort;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final LogoutUseCase logoutUseCase;

    /*
     * @Transactional 을 붙이지 않는다. 메일 발송이 트랜잭션 안에 들어가면 SMTP 가 느린 만큼
     * DB 커넥션을 쥐고 있게 되고, 저장 뒤 갱신표 폐기도 커밋 전에 실행된다
     * (AuthService.changePassword 와 같은 이유). 저장의 트랜잭션 경계는 MemberPasswordPort
     * 구현이 자기 안에 가진다.
     */
    @Override
    public void resetPassword(ResetPasswordCommand command) {
        String subject = RateLimitSubject.ofAccount(command.companyCode(), command.email());
        RateLimitPolicy policy = rateLimitProperties.passwordResetPolicy();

        if (!rateLimiter.peek(policy, subject).allowed()) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_REQUESTS);
        }

        Company company = companyRepository.findByCode(normalize(command.companyCode())).orElse(null);
        MemberCredentials member = company == null ? null
                : memberAuthQueryPort.findForLogin(company.id(), command.email()).orElse(null);

        if (member == null || member.resigned()) {
            // 못 찾은 요청도 센다 — 세지 않으면 이 응답이 계정 목록을 훑는 도구가 된다.
            rateLimiter.record(policy, subject);
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_ACCOUNT_NOT_FOUND);
        }

        String newPassword = passwordGenerator.generate();

        /*
         * 저장보다 메일이 먼저다. 반대로 하면 발송이 실패했을 때 사용자는 새 비밀번호를 모르는데
         * 옛 비밀번호는 이미 못 쓰는 상태가 되고, 관리자 재발급 경로도 없어 계정이 영영 잠긴다.
         * 이 순서면 최악이라도 "메일 속 비밀번호가 안 먹는다"에서 끝나고, 기존 비밀번호는 살아 있다.
         */
        // 사용자가 친 값이 아니라 저장된 기업 코드를 싣는다 — 소문자로 쳤어도 메일에는 정본이 간다.
        if (!accountMailPort.sendPasswordReset(command.email(), company.code(), newPassword)) {
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_MAIL_FAILED);
        }

        memberPasswordPort.resetPassword(member.memberId(), member.companyId(),
                passwordEncoder.encode(newPassword));

        // 옛 비밀번호로 열어 둔 세션을 남겨 두면 비밀번호를 바꾼 의미가 없다.
        logoutUseCase.logout(member.memberId());

        rateLimiter.record(policy, subject);
    }

    /** 메일에서 복사하면 앞뒤 공백이 붙고 대소문자도 섞여 들어온다(AuthService.login 과 같은 규칙). */
    private String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }
}
