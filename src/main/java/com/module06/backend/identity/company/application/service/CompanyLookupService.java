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

    private String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }
}
