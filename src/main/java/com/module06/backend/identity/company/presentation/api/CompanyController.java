package com.module06.backend.identity.company.presentation.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.company.application.usecase.GetCompanyProfileUseCase;
import com.module06.backend.identity.company.application.usecase.LookupCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.OnboardCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.RegisterCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.UpdateCompanyProfileUseCase;
import com.module06.backend.identity.company.presentation.api.dto.request.CompanyLookupRequest;
import com.module06.backend.identity.company.presentation.api.dto.request.CompanyRegistrationRequest;
import com.module06.backend.identity.company.presentation.api.dto.request.OnboardingRequest;
import com.module06.backend.identity.company.presentation.api.dto.request.UpdateCompanyRequest;
import com.module06.backend.identity.company.presentation.api.dto.response.CompanyLookupResponse;
import com.module06.backend.identity.company.presentation.api.dto.response.CompanyProfileResponse;
import com.module06.backend.identity.company.presentation.api.dto.response.CompanyRegistrationResponse;
import com.module06.backend.identity.company.presentation.api.dto.response.OnboardingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;


@Tag(name = "Identity", description = "인증 · 기업 조회 API")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final LookupCompanyUseCase lookupCompanyUseCase;
    private final RegisterCompanyUseCase registerCompanyUseCase;
    private final GetCompanyProfileUseCase getCompanyProfileUseCase;
    private final UpdateCompanyProfileUseCase updateCompanyProfileUseCase;
    private final OnboardCompanyUseCase onboardCompanyUseCase;

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

    @Operation(summary = "온보딩 커밋", description = "부서·역할, 직급, 초대 명단을 한 번에 저장하고 계정을 즉시 발급합니다. "
            + "재호출은 막힙니다(이미 온보딩된 기업).")
    @PostMapping("/me/onboarding")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<OnboardingResponse> onboard(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody OnboardingRequest request) {
        OnboardingResponse response = OnboardingResponse.from(
                onboardCompanyUseCase.onboard(request.toCommand(companyId)));
        return ApiResponse.success("온보딩을 완료했습니다", response);
    }

    @Operation(summary = "기업 기본 정보 조회", description = "기업 설정 > 기본 정보 탭.")
    @GetMapping("/me")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<CompanyProfileResponse> getProfile(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        CompanyProfileResponse response = CompanyProfileResponse.from(
                getCompanyProfileUseCase.getProfile(companyId));
        return ApiResponse.success("기업 정보를 조회했습니다", response);
    }

    @Operation(summary = "기업 기본 정보 수정", description = "부분 수정 — 보낸 필드만 바뀝니다. code는 수정할 수 없습니다.")
    @PatchMapping("/me")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<CompanyProfileResponse> updateProfile(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody UpdateCompanyRequest request) {
        CompanyProfileResponse response = CompanyProfileResponse.from(
                updateCompanyProfileUseCase.updateProfile(request.toCommand(companyId)));
        return ApiResponse.success("기업 정보를 수정했습니다", response);
    }
}
