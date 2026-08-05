package com.module06.backend.identity.auth.application.usecase;

/**
 * API 03 — 갱신표로 새 액세스 토큰을 받는다.
 *
 * <p>갱신표도 함께 교체된다(로테이션). 쓴 표를 남겨두면 탈취된 표가 적힌 수명 내내 통하고,
 * 서버가 그걸 취소할 방법이 없다.
 */
public interface ReissueTokenUseCase {

    /**
     * @param keepSignedIn 새 갱신표의 수명을 가른다(1일 ↔ 14일). 재발급마다 다시 받는 이유는,
     *                     로그인할 때의 선택을 서버가 들고 있지 않기 때문이다 — 갱신표에는
     *                     memberId 와 jti 만 실린다.
     */
    ReissuedTokens reissue(String refreshToken, boolean keepSignedIn);

    record ReissuedTokens(String accessToken, String refreshToken) {
    }
}
