package com.module06.backend.identity.member.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.member.application.dto.IssuedMember;
import com.module06.backend.identity.member.application.usecase.IssueMemberUseCase;
import com.module06.backend.identity.member.presentation.api.dto.request.IssueMemberRequest;
import com.module06.backend.identity.member.presentation.api.dto.response.IssuedMemberResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "계정 발급 API")
@RestController
@RequestMapping("/api/manage/members")
@RequiredArgsConstructor
public class ManageMemberController {

    private final IssueMemberUseCase issueMemberUseCase;

    @Operation(summary = "계정 발급", description = "임시 비밀번호를 생성해 기업코드·이메일·비밀번호를 메일로 보냅니다. "
            + "발급 즉시 재직 상태입니다.")
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<IssuedMemberResponse> issue(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody IssueMemberRequest request) {
        IssuedMember issued = issueMemberUseCase.issue(request.toCommand(companyId));
        return ApiResponse.success("계정을 발급했습니다. 계정 정보를 메일로 보냈어요.", IssuedMemberResponse.from(issued));
    }
}
