package com.module06.backend.identity.company.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.OnboardCompanyCommand;
import com.module06.backend.identity.company.application.dto.OnboardingResult;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.application.usecase.OnboardCompanyUseCase;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

/** §4-1. 커밋(트랜잭션) → 메일 발송(트랜잭션 밖)을 오케스트레이션한다. */
@Service
@RequiredArgsConstructor
public class CompanyOnboardingService implements OnboardCompanyUseCase {

    private final CompanyOnboardingCommitter committer;
    private final CompanyRepository companyRepository;
    private final AccountMailPort accountMailPort;

    @Override
    public OnboardingResult onboard(OnboardCompanyCommand command) {
        CompanyOnboardingCommitter.CommitResult result = committer.commit(command);

        Company company = companyRepository.findById(command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.COMPANY_CODE_NOT_FOUND));

        List<OnboardingResult.IssuedAccount> issued = result.issuedAccounts().stream()
                .map(account -> {
                    /*
                     * §5-1과 같은 행별 try/catch — 메일 한 통이 실패해도 이미 커밋된 나머지 계정
                     * 발급을 되돌릴 이유가 없다(AccountMailPort 계약상 원래 예외를 던지지 않지만,
                     * 방어적으로 감싼다).
                     */
                    try {
                        accountMailPort.sendAccountIssued(account.email(), company.code(), account.password());
                        return new OnboardingResult.IssuedAccount(account.email(), "SENT");
                    } catch (RuntimeException exception) {
                        return new OnboardingResult.IssuedAccount(account.email(), "MAIL_FAILED");
                    }
                })
                .toList();

        List<OnboardingResult.SkippedInvite> skipped = result.skipped().stream()
                .map(s -> new OnboardingResult.SkippedInvite(s.email(), s.reason()))
                .toList();

        return new OnboardingResult(result.onboardedAt(), result.teamCount(), result.subTeamCount(),
                result.jobPositionCount(), issued, skipped);
    }
}
