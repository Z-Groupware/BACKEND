package com.module06.backend.identity.company.presentation.api.dto.response;

import com.module06.backend.identity.company.domain.model.Company;

/**
 * 코드와 이름 둘만 내린다.
 *
 * <p>토큰 없이 부를 수 있는 API 라 필드를 늘리는 만큼 그대로 샌다 — 구성원 수나 플랜을 넣으면
 * 기업코드 하나로 회사 규모가 조회된다. id 도 내리지 않는다.
 */
public record CompanyLookupResponse(String code, String name) {

    public static CompanyLookupResponse from(Company company) {
        return new CompanyLookupResponse(company.code(), company.name());
    }
}
