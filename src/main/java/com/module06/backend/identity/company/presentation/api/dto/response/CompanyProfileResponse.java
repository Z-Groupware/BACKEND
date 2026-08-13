package com.module06.backend.identity.company.presentation.api.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.module06.backend.identity.company.domain.model.Company;

/** §4-2·4-3 공통 응답. {@code subscriptionStatus}는 구독 도메인이 채워 준다(호출자가 주입). */
@Schema(description = "기업 기본 정보")
public record CompanyProfileResponse(
        Long companyId,
        String code,
        String name,
        String businessNumber,
        String representativeName,
        String address,

        @Schema(description = "회사 위치 위도 — 지도로 고른 적이 없으면 null", example = "37.5006")
        Double latitude,

        @Schema(description = "회사 위치 경도 — 지도로 고른 적이 없으면 null", example = "127.0366")
        Double longitude,

        String phone,
        String subscriptionStatus,
        LocalDateTime onboardedAt
) {
    public static CompanyProfileResponse from(Company company, String subscriptionStatus) {
        return new CompanyProfileResponse(
                company.id(), company.code(), company.name(), company.registrationNo(),
                company.representativeName(), company.address(), company.latitude(), company.longitude(),
                company.mainPhone(), subscriptionStatus, company.onboardedAt());
    }
}
