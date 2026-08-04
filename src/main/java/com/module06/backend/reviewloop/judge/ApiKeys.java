package com.module06.backend.reviewloop.judge;

/**
 * API 키 정규화·검증 — 어댑터 공통(단일 진실 공급원). {@link GeminiModels}와 같은 자리다.
 *
 * <p><b>왜 필요한가(실제 사고 기록)</b>: 환경변수 값 끝에 개행이 붙는 일은 흔하다
 * (복사·붙여넣기, {@code echo}로 파일에 기록, setx 등). JDK {@code HttpClient}는 헤더 값에 개행을
 * 허용하지 않아 {@code IllegalArgumentException}을 던지는데, <b>그 예외 메시지에 키 값이 그대로 들어간다.</b>
 * 이 저장소에서 실제로 {@code GEMINI_API_KEY} 끝의 LF 때문에
 * <b>판정이 매번 크래시했고(감사 로그 0건), 키가 콘솔에 찍혔다.</b>
 *
 * <p>그래서 키는 어댑터 진입점에서 <b>한 번</b> 정규화하고, 그래도 못 쓰는 값이면
 * <b>값을 로그에 남기지 않는</b> 예외로 즉시 멈춘다. 어댑터마다 따로 두면 한 곳만 고쳐지므로 여기 모은다.
 */
final class ApiKeys {

    /**
     * 앞뒤 공백·개행을 떼고 반환한다. 비었거나 <b>가운데에</b> 제어문자가 남아 있으면 예외.
     *
     * @param raw     환경변수 원본 값(null 허용)
     * @param envName 오류 메시지에 쓸 환경변수 이름 — 키 값은 절대 메시지에 넣지 않는다
     */
    static String require(String raw, String envName) {
        String key = raw == null ? "" : raw.strip();
        if (key.isEmpty()) {
            throw new IllegalStateException(envName + " 환경변수가 필요합니다.");
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                // 값 가운데의 제어문자는 strip으로 못 없앤다 → 헤더로 쓸 수 없다. 값은 절대 출력하지 않는다.
                throw new IllegalStateException(envName + " 값에 제어문자가 섞여 있어 HTTP 헤더로 쓸 수 없습니다"
                        + " (길이 " + key.length() + " · 위치 " + i + "). 값은 로그에 남기지 않는다 — 환경변수를 다시 설정할 것.");
            }
        }
        return key;
    }

    /**
     * 정규화 후에도 값이 남아 있는가 — "키 없음 → LLM 게이트 생략·통과" 판단용.
     * {@code isBlank()} 직접 검사와 달리 {@link #require}와 같은 기준을 쓴다(판단과 사용이 갈리지 않게).
     */
    static boolean present(String raw) {
        return raw != null && !raw.strip().isEmpty();
    }

    private ApiKeys() {
    }
}
