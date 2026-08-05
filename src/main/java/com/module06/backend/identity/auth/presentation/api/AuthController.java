package com.module06.backend.identity.auth.presentation.api;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase;
import com.module06.backend.identity.auth.presentation.api.dto.request.LoginRequest;
import com.module06.backend.identity.auth.presentation.api.dto.request.ReissueTokenRequest;
import com.module06.backend.identity.auth.presentation.api.dto.response.ReissuedTokenResponse;
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
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final LogoutUseCase logoutUseCase;

    @Operation(summary = "로그인", description = "로그인 2단계. 기업 코드·이메일·비밀번호로 토큰을 발급합니다.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = TokenResponse.from(loginUseCase.login(request.toCommand()));
        return ApiResponse.success("로그인되었습니다", response);
    }

    @Operation(summary = "토큰 재발급",
            description = "갱신표로 새 액세스 토큰을 받습니다. 갱신표도 함께 교체됩니다(로테이션).")
    @PostMapping("/refresh")
    public ApiResponse<ReissuedTokenResponse> refresh(@Valid @RequestBody ReissueTokenRequest request) {
        ReissuedTokenResponse response = ReissuedTokenResponse.from(
                reissueTokenUseCase.reissue(request.refreshToken(), request.keepSignedInOrFalse()));
        return ApiResponse.success("토큰을 재발급했습니다", response);
    }

    /*
     * 대상을 바디로 받지 않고 토큰에서 꺼낸다. 바디로 받으면 남의 memberId 를 넣어
     * 다른 사람을 로그아웃시킬 수 있다.
     */
    @Operation(summary = "로그아웃",
            description = "갱신표를 폐기합니다. 이미 발급된 액세스 토큰은 남은 수명(30분)까지 유효합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal me) {
        logoutUseCase.logout(me.memberId());
        return ApiResponse.successWithoutData("로그아웃되었습니다");
    }
}
