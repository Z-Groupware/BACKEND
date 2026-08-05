package com.module06.backend.identity.company.presentation.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.company.application.usecase.LookupCompanyUseCase;
import com.module06.backend.identity.company.presentation.api.dto.request.CompanyLookupRequest;
import com.module06.backend.identity.company.presentation.api.dto.response.CompanyLookupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;


@Tag(name = "Identity", description = "인증 · 기업 조회 API")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final LookupCompanyUseCase lookupCompanyUseCase;

    @Operation(summary = "기업코드 조회", description = "로그인 1단계. 기업 코드로 회사 이름을 확인합니다.")
    @PostMapping("/lookup")
    public ApiResponse<CompanyLookupResponse> lookup(@Valid @RequestBody CompanyLookupRequest request) {
        CompanyLookupResponse response = CompanyLookupResponse.from(
                lookupCompanyUseCase.lookup(request.code()));
        return ApiResponse.success("기업 정보를 확인했습니다", response);
    }
}
