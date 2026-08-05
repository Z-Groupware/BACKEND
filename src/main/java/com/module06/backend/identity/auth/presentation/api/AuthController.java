package com.module06.backend.identity.auth.presentation.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.presentation.api.dto.request.LoginRequest;
import com.module06.backend.identity.auth.presentation.api.dto.response.TokenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "인증 · 기업 조회 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    @Operation(summary = "로그인", description = "로그인 2단계. 기업 코드·이메일·비밀번호로 토큰을 발급합니다.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = TokenResponse.from(loginUseCase.login(request.toCommand()));
        return ApiResponse.success("로그인되었습니다", response);
    }
}
