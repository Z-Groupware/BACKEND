package com.module06.backend.identity.auth.presentation.api.dto.response;

import com.module06.backend.identity.auth.application.dto.LoginResult;

/**
 * 토큰을 응답 본문으로 내리고 쿠키를 굽지 않는다. 프론트(Next BFF)가 받아 httpOnly 쿠키로 다시 굽는
 * 구조이므로, 서버가 쿠키를 구우면 도메인·SameSite 를 두 곳에서 관리하게 된다.
 *
 * <p>명세 §2-2 의 {@code mustChangePassword} 는 담지 않는다 — 첫 로그인 비밀번호 강제 변경 기능을
 * 만들지 않기로 했다. 항상 {@code false} 를 내리는 필드는 프론트에 잘못된 기대만 남긴다.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String landingPath
) {

    public static TokenResponse from(LoginResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken(), result.landingPath());
    }
}
