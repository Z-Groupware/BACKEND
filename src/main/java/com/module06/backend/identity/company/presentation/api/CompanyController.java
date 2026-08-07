package com.module06.backend.identity.company.presentation.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.company.application.usecase.LookupCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.RegisterCompanyUseCase;
import com.module06.backend.identity.company.presentation.api.dto.request.CompanyLookupRequest;
import com.module06.backend.identity.company.presentation.api.dto.request.CompanyRegistrationRequest;
import com.module06.backend.identity.company.presentation.api.dto.response.CompanyLookupResponse;
import com.module06.backend.identity.company.presentation.api.dto.response.CompanyRegistrationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;


@Tag(name = "Identity", description = "인증 · 기업 조회 API")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final LookupCompanyUseCase lookupCompanyUseCase;
    private final RegisterCompanyUseCase registerCompanyUseCase;

    @Operation(summary = "기업코드 조회", description = "로그인 1단계. 기업 코드로 회사 이름을 확인합니다.")
    @PostMapping("/lookup")
    public ApiResponse<CompanyLookupResponse> lookup(@Valid @RequestBody CompanyLookupRequest request) {
        CompanyLookupResponse response = CompanyLookupResponse.from(
                lookupCompanyUseCase.lookup(request.code()));
        return ApiResponse.success("기업 정보를 확인했습니다", response);
    }

    /**
     * 운영자 승인 절차가 없다 — 제출 즉시 기업과 오너 계정이 만들어지고 메일이 나간다.
     *
     * <p>비밀번호는 응답에 없다. 메일로만 간다.
     */
    @Operation(summary = "기업 등록 신청",
            description = "기업과 오너 계정을 생성하고 기업코드·이메일·비밀번호를 메일로 보냅니다. "
                    + "승인 절차가 없어 신청 즉시 이용할 수 있습니다.")
    @PostMapping("/registrations")
    public ApiResponse<CompanyRegistrationResponse> register(
            @Valid @RequestBody CompanyRegistrationRequest request) {
        CompanyRegistrationResponse response = CompanyRegistrationResponse.from(
                registerCompanyUseCase.register(request.toCommand()));
        return ApiResponse.created("기업 등록이 완료되었습니다. 계정 정보를 메일로 보냈어요.", response);
    }
}
