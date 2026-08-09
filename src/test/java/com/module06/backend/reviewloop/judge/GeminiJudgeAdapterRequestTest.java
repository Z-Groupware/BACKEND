package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판정 요청의 재현성 설정 검증 — 네트워크·키 없이 요청 본문만 본다(LiveTest와 별개).
 * 같은 코드에 같은 판정이 나와야 게이트가 성립한다: temperature=0 + 모델 버전 고정.
 */
class GeminiJudgeAdapterRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode generationConfig() throws IOException {
        GeminiJudgeAdapter adapter = new GeminiJudgeAdapter("dummy-key", "gemini-3.5-flash");
        return mapper.readTree(adapter.buildRequest("A.java", "class A {}", "정책"))
                .path("generationConfig");
    }

    @Test
    @DisplayName("요청 본문에 temperature=0 이 실린다 (미설정 시 기본값≈1.0이라 판정이 흔들림)")
    void sendsTemperatureZero() throws IOException {
        JsonNode gen = generationConfig();

        assertThat(gen.has("temperature")).isTrue();
        assertThat(gen.path("temperature").asInt()).isZero();
    }

    @Test
    @DisplayName("응답 스키마 강제는 그대로 유지된다")
    void keepsResponseSchema() throws IOException {
        JsonNode gen = generationConfig();

        assertThat(gen.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(gen.path("responseSchema").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("고정 모델은 '-latest' 별칭이 아니다")
    void pinnedModelIsNotAnAlias() {
        assertThat(GeminiModels.PINNED)
                .as("별칭은 Google이 모델을 교체하면 같은 코드에 판정이 바뀐다")
                .doesNotEndWith("-latest")
                .isNotBlank();
    }

    @Test
    @DisplayName("어댑터들이 모델 문자열을 각자 갖지 않는다 (SSOT)")
    void allAdaptersResolveFromOneSource() {
        // 어댑터마다 문자열을 따로 두면 모델 교체 시 일부만 고쳐져 서로 다른 모델을 쓰게 된다.
        assertThat(GeminiModels.resolve())
                .isEqualTo(System.getenv().getOrDefault("GEMINI_MODEL", GeminiModels.PINNED));
    }
}
