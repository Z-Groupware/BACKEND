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
     * 전체 교체가 된다. 현재 값을 미리 읽어 병합하지 않는다 — 동시에 다른 필드를 고친 PATCH가
     * 그 사이 커밋되면, 이 요청이 안 건드린 필드까지 자기가 읽은 낡은 스냅샷 값으로 되돌려
     * 먼저 커밋된 변경을 지워버린다(lost update, 코드래빗 지적). 대신 null 을 그대로
     * {@link CompanyProfileRepository#updateProfile} 까지 흘려보내, 엔티티가 "지금 갖고 있는 값
     * 위에 바뀐 필드만 얹는" 방식으로 병합하게 한다.
     */
    @Override
    @Transactional
    public Company updateProfile(UpdateCompanyCommand command) {
        if (command.registrationNo() != null) {
            assertRegistrationNoValid(command.registrationNo(), command.companyId());
        }

        companyProfileRepository.updateProfile(command.companyId(), command.name(), command.registrationNo(),
                command.representativeName(), command.address(), command.mainPhone());

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
