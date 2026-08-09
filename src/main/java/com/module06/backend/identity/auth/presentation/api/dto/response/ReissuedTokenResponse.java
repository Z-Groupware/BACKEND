package com.module06.backend.identity.auth.presentation.api.dto.response;

import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase.ReissuedTokens;

/**
 * 재발급 응답. 착지 경로를 담지 않는다 — 재발급은 배경에서 토큰만 갈아끼우는 호출이고,
 * 화면을 옮기면 사용자가 하던 일이 끊긴다. 착지는 로그인({@link TokenResponse})만의 몫이다.
 */
public record ReissuedTokenResponse(String accessToken, String refreshToken) {

    public static ReissuedTokenResponse from(ReissuedTokens tokens) {
        return new ReissuedTokenResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
