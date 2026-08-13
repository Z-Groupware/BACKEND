package com.module06.backend.identity.auth.application.usecase;

/**
 * API 03 — 갱신표로 새 액세스 토큰을 받는다.
 *
 * <p>갱신표도 함께 교체된다(로테이션). 쓴 표를 남겨두면 탈취된 표가 적힌 수명 내내 통하고,
 * 서버가 그걸 취소할 방법이 없다.
 */
public interface ReissueTokenUseCase {

    /**
     * 새 갱신표의 수명(1일 ↔ 14일)은 <b>로그인할 때의 선택</b>을 그대로 따른다. 그 선택은 갱신표의
     * {@code kis} 클레임에 실려 있으므로 여기서 받지 않는다 — 받으면 1일짜리 세션을 재발급 한 번으로
     * 14일로 승급시킬 수 있다.
     *
     * <p>{@code authTime} 클레임(최초 로그인 시각)도 함께 승계되어, 갱신을 반복해도 세션의 절대
     * 나이가 리셋되지 않는다. 상한({@code jwt.refresh-absolute-max})을 넘으면 재로그인을 요구한다.
     */
    ReissuedTokens reissue(String refreshToken);

    record ReissuedTokens(String accessToken, String refreshToken) {
    }
}
