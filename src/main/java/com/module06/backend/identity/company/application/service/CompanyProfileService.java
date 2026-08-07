package com.module06.backend.identity.company.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.UpdateCompanyCommand;
import com.module06.backend.identity.company.application.usecase.GetCompanyProfileUseCase;
import com.module06.backend.identity.company.application.usecase.UpdateCompanyProfileUseCase;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyProfileRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import lombok.RequiredArgsConstructor;

/** §4-2·4-3. 기업 설정 > 기본 정보 탭. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyProfileService implements GetCompanyProfileUseCase, UpdateCompanyProfileUseCase {

    private static final String REGISTRATION_NO_PATTERN = "\\d{3}-\\d{2}-\\d{5}";

    private final CompanyRepository companyRepository;
    private final CompanyProfileRepository companyProfileRepository;

    @Override
    public Company getProfile(Long companyId) {
        return findCompany(companyId);
    }

    /**
     * null 인 필드는 값을 바꾸지 않는다 — 요청에 없는 필드까지 지워버리면 부분 수정이 아니라
     * 전체 교체가 된다. 현재 값을 먼저 읽어 요청과 병합한 뒤 통째로 다시 쓴다.
     */
    @Override
    @Transactional
    public Company updateProfile(UpdateCompanyCommand command) {
        Company current = findCompany(command.companyId());

        String name = command.name() != null ? command.name() : current.name();
        String registrationNo = command.registrationNo() != null ? command.registrationNo() : current.registrationNo();
        String representativeName = command.representativeName() != null
                ? command.representativeName() : current.representativeName();
        String address = command.address() != null ? command.address() : current.address();
        String mainPhone = command.mainPhone() != null ? command.mainPhone() : current.mainPhone();

        if (command.registrationNo() != null) {
            assertRegistrationNoValid(registrationNo, command.companyId());
        }

        companyProfileRepository.updateProfile(command.companyId(), name, registrationNo,
                representativeName, address, mainPhone);

        return findCompany(command.companyId());
    }

    private void assertRegistrationNoValid(String registrationNo, Long companyId) {
        if (!registrationNo.matches(REGISTRATION_NO_PATTERN)) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_NO_INVALID);
        }
        if (companyProfileRepository.existsByRegistrationNoAndIdNot(registrationNo, companyId)) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_NO_DUPLICATED);
        }
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.COMPANY_CODE_NOT_FOUND));
    }
}
