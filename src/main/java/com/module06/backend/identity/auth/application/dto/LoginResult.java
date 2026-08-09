package com.module06.backend.identity.auth.application.dto;

/**
 * 프로필을 담지 않는다 — 프론트가 곧바로 {@code GET /api/auth/me} 를 부른다(명세 §2-2). 두 곳에서
 * 같은 값을 내리면 한쪽만 고쳐졌을 때 화면이 갈린다.
 */
public record LoginResult(
        String accessToken,
        String refreshToken,
        String landingPath
) {
}
