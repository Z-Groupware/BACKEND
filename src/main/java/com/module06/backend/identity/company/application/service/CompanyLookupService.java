package com.module06.backend.identity.company.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.port.out.LookupRateLimiter;
import com.module06.backend.identity.company.application.usecase.LookupCompanyUseCase;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 1단계. 기업코드로 회사를 찾아 이름을 돌려준다.
 *
 * <p>레이트 리밋을 조회보다 먼저 건다 — 초과한 요청이 DB 까지 가면 제한의 의미가 없다.
 */
@Service
@RequiredArgsConstructor
public class CompanyLookupService implements LookupCompanyUseCase {

    private final CompanyRepository companyRepository;
    private final LookupRateLimiter rateLimiter;

    @Override
    @Transactional(readOnly = true)
    public Company lookup(String rawCode, String clientIp) {
        rateLimiter.checkOrThrow(clientIp);
        return companyRepository.findByCode(normalize(rawCode))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.COMPANY_CODE_NOT_FOUND));
    }

    /** 메일에서 복사하면 앞뒤 공백이 붙고, 대소문자도 섞여 들어온다. */
    private String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }
}
