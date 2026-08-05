package com.module06.backend.identity.company.presentation.api;

import jakarta.servlet.http.HttpServletRequest;
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

/**
 * 로그인 1단계 API.
 *
 * <p>GET 쿼리가 아니라 POST 바디인 이유: 쿼리스트링은 액세스 로그·프록시 로그·브라우저 히스토리·
 * Referer 에 기업코드를 그대로 남긴다.
 */
@Tag(name = "Identity", description = "인증 · 기업 조회 API")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final LookupCompanyUseCase lookupCompanyUseCase;

    @Operation(summary = "기업코드 조회", description = "로그인 1단계. 기업 코드로 회사 이름을 확인합니다.")
    @PostMapping("/lookup")
    public ApiResponse<CompanyLookupResponse> lookup(@Valid @RequestBody CompanyLookupRequest request,
                                                     HttpServletRequest servletRequest) {
        CompanyLookupResponse response = CompanyLookupResponse.from(
                lookupCompanyUseCase.lookup(request.code(), servletRequest.getRemoteAddr()));
        return ApiResponse.success("기업 정보를 확인했습니다", response);
    }
}
