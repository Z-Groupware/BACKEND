package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 사고에 대한 회귀 테스트.
 *
 * GEMINI_API_KEY 끝에 개행이 붙어 있어 JDK HttpClient가 헤더 값을 거부했고,
 * 판정이 매번 크래시했다(감사 로그 0건). 게다가 JDK 예외 메시지에 키 값이 그대로 실려 콘솔에 노출됐다.
 * 그래서 여기서 두 가지를 고정한다: ① 정규화한 키는 HTTP 헤더로 쓸 수 있다 ② 오류 메시지에 키가 없다.
 */
class ApiKeysTest {

    private static final String KEY = "AIzaSyExampleKey_1234567890";

    @Test
    @DisplayName("끝에 개행이 붙은 키는 정규화되어 HTTP 헤더로 쓸 수 있다 (실제 사고 회귀)")
    void stripsTrailingNewlineSoHeaderIsAccepted() {
        String normalized = ApiKeys.require(KEY + "\n", "GEMINI_API_KEY");

        assertThat(normalized).isEqualTo(KEY);
        // 정규화 전 값으로는 이 호출이 IllegalArgumentException을 던졌다 — 그게 크래시의 정체다.
        assertThatCode(() -> HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://example.invalid"))
                .header("x-goog-api-key", normalized)
                .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CRLF·앞뒤 공백·탭도 제거한다")
    void stripsSurroundingWhitespace() {
        assertThat(ApiKeys.require("  " + KEY + "\r\n", "GEMINI_API_KEY")).isEqualTo(KEY);
        assertThat(ApiKeys.require("\t" + KEY + "\t", "GEMINI_API_KEY")).isEqualTo(KEY);
    }

    @Test
    @DisplayName("null·빈 값·공백만 있는 값은 '환경변수 필요' 예외")
    void rejectsMissingKey() {
        for (String bad : new String[]{null, "", "   ", "\n"}) {
            assertThatThrownBy(() -> ApiKeys.require(bad, "GEMINI_API_KEY"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GEMINI_API_KEY");
        }
    }

    @Test
    @DisplayName("가운데 제어문자가 섞이면 예외 — 그리고 메시지에 키 값이 없다(자격증명 유출 방지)")
    void rejectsEmbeddedControlCharWithoutLeakingValue() {
        String tampered = "AIzaSyExample\nKey_1234567890";

        assertThatThrownBy(() -> ApiKeys.require(tampered, "GEMINI_API_KEY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("제어문자")
                // 핵심: 값의 어떤 조각도 메시지에 실리지 않아야 한다(JDK 예외가 하던 실수).
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("AIzaSy");
                    assertThat(e.getMessage()).doesNotContain("Key_1234567890");
                });
    }

    @Test
    @DisplayName("present()는 require()와 같은 기준 — 개행만 붙은 키는 '있음'")
    void presentUsesSameRuleAsRequire() {
        assertThat(ApiKeys.present(KEY + "\n")).isTrue();     // 있음 → 게이트가 판정을 시도해야 한다
        assertThat(ApiKeys.present("   ")).isFalse();          // 없음 → 게이트 생략·통과
        assertThat(ApiKeys.present(null)).isFalse();
    }
}
