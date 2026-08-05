package com.module06.backend.identity.company.presentation.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompanyLookupRequest(
        @NotBlank(message = "기업 코드를 입력해 주세요.")
        String code
) {
}
