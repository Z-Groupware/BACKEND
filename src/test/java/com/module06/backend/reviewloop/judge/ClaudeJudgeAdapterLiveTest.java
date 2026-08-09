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
 * Gate 2 · 실제 LLM(claude-opus-4-8) 스모크 테스트.
 * ANTHROPIC_API_KEY가 있을 때만 실행된다(없으면 skip → CI/로컬 기본 빌드는 영향 없음).
 * 씨앗 위반(PERF_001 N+1)을 실제 Judge가 잡는지 확인하는 최소 recall 스모크.
 * 정식 recall 실측은 Promptfoo 골든셋(다음 스텝)이 담당한다.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeJudgeAdapterLiveTest {

    private static final String PERF_POLICY = """
            PERF_001 (N+1 금지): 반복문 안에서 엔티티 1건당 리포지토리를 호출하면 N+1이다.
            다중 조회는 IN 절 배치(findByIdIn 등)로 한 번에 가져와야 한다. 위반 시 severity=MINOR.
            """;

    @Test
    @DisplayName("씨앗 N+1 코드에서 PERF_001 finding을 잡는다")
    void detectsSeededN1() throws IOException {
        String code = Files.readString(Path.of("review-loop/golden/perf001/QuizListN1.java.txt"));

        List<Finding> findings = new ClaudeJudgeAdapter().review("QuizListN1.java", code, PERF_POLICY);

        assertThat(findings)
                .as("N+1 씨앗 코드에서 최소 1건의 finding을 기대")
                .isNotEmpty();
    }
}
