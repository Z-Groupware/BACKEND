package com.module06.backend.identity.company.presentation.api.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.module06.backend.identity.company.domain.model.Company;

/** §4-2·4-3 공통 응답. {@code plan}은 결제 연동이 없어 "FREE" 고정이다. */
@Schema(description = "기업 기본 정보")
public record CompanyProfileResponse(
        Long companyId,
        String code,
        String name,
        String businessNumber,
        String representativeName,
        String address,
        String phone,
        String plan,
        LocalDateTime onboardedAt
) {
    public static CompanyProfileResponse from(Company company) {
        return new CompanyProfileResponse(
                company.id(), company.code(), company.name(), company.registrationNo(),
                company.representativeName(), company.address(), company.mainPhone(),
                "FREE", company.onboardedAt());
    }
}
