package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 실제 LLM(Gemini) 스모크 테스트 — 보유한 Gemini 키로 라이브 확인.
 * GEMINI_API_KEY가 있을 때만 실행(없으면 skip → 기본 빌드 영향 없음).
 * 씨앗 N+1 코드를 실제 Judge가 finding으로 잡는지 확인. (provider만 Gemini, 나머지 루프는 동일)
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiJudgeAdapterLiveTest {

    private static final String PERF_POLICY = """
            PERF_001 (N+1 금지): 반복문 안에서 엔티티 1건당 리포지토리를 호출하면 N+1이다.
            다중 조회는 IN 절 배치(findByIdIn 등)로 한 번에 가져와야 한다. 위반 시 severity=MINOR.
            """;

    @Test
    @DisplayName("씨앗 N+1 코드에서 finding을 잡는다 (Gemini)")
    void detectsSeededN1() throws IOException {
        String code = Files.readString(Path.of("review-loop/golden/perf001/QuizListN1.java.txt"));

        List<Finding> findings = new GeminiJudgeAdapter().review("QuizListN1.java", code, PERF_POLICY);

        assertThat(findings)
                .as("N+1 씨앗 코드에서 최소 1건의 finding을 기대")
                .isNotEmpty();

        // 데모: 실제로 뭐라고 잡았는지 파일로 남긴다(확인용).
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/gemini-findings.txt"),
                findings.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
    }
}
