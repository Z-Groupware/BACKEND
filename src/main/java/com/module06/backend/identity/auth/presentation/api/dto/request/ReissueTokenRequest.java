package com.module06.backend.identity.auth.presentation.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 필드 이름은 명세 §2-3 그대로다 — 프론트가 이 키로 보낸다.
 *
 * <p><b>{@code keepSignedIn} 은 더 이상 받지 않는다.</b> 예전에는 로그인 시점의 선택을 서버가
 * 들고 있지 않아 재발급마다 다시 물었는데, 그 구조에서는 "로그인 유지"를 끄고 받은 1일짜리 세션을
 * 재발급 요청에 {@code true} 하나 실어 14일로 승급시킬 수 있었다. 지금은 그 선택이 갱신표의
 * {@code kis} 클레임(서명 안이라 위조 불가)에 실려 승계된다.
 *
 * <p>프론트가 이 키를 계속 보내도 400 이 되지 않는다 — Spring Boot 의 Jackson 기본값이 모르는
 * 필드를 무시한다. 그래서 FE 배포를 기다리지 않고 넣을 수 있다(값은 무시되고 서버 판단이 이긴다).
 *
 * <p>실패 메시지가 "다시 로그인해 주세요" 인 이유: 갱신표가 비었다는 건 프론트가 저장해 둔 값을
 * 잃었다는 뜻이고, 그때 할 수 있는 일은 재로그인뿐이다.
 */
public record ReissueTokenRequest(
        @NotBlank(message = "다시 로그인해 주세요.")
        String refreshToken
) {
}
