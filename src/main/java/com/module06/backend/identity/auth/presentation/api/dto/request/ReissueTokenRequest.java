package com.module06.backend.identity.auth.presentation.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 필드 이름은 명세 §2-3 그대로다 — 프론트가 이 키로 보낸다.
 *
 * <p>{@code keepSignedIn} 을 {@link LoginRequest} 와 같은 이유로 {@code Boolean} 으로 받는다 —
 * 원시 {@code boolean} 은 키를 빼고 보내면 역직렬화가 실패해 400 이 된다. 재발급은 화면 전환 없이
 * 배경에서 도는 호출이라, 여기서 400 이 나면 사용자에게는 앱이 멈춘 것처럼 보인다.
 *
 * <p>실패 메시지가 "다시 로그인해 주세요" 인 이유: 갱신표가 비었다는 건 프론트가 저장해 둔 값을
 * 잃었다는 뜻이고, 그때 할 수 있는 일은 재로그인뿐이다.
 */
public record ReissueTokenRequest(
        @NotBlank(message = "다시 로그인해 주세요.")
        String refreshToken,

        Boolean keepSignedIn
) {

    public boolean keepSignedInOrFalse() {
        return Boolean.TRUE.equals(keepSignedIn);
    }
}
