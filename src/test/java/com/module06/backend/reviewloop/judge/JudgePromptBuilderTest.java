package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 프롬프트 자동 조립 검증 — rules.yaml(SSOT)에서 judge 규칙만 뽑아 프롬프트가 되는지.
 * LLM 없음(순수 코드).
 */
class JudgePromptBuilderTest {

    private static final String YAML = """
            rules:
              - id: SEC_001
                text: "인증 만료값 고정"
                severity: CRITICAL
                enforced_by: test
              - id: ARCH_001
                text: "컨트롤러 로직 금지"
                severity: MINOR
                enforced_by: archunit
              - id: MIG_001
                text: "마이그레이션으로만"
                severity: CRITICAL
                enforced_by: [test, ci]
              - id: CONV_001
                text: "유틸 재발명 금지"
                severity: MINOR
                enforced_by: judge
              - id: ARCH_003a
                text: "타 도메인 JpaEntity 투영 예외"
                severity: MINOR
                enforced_by: judge
            notes:
              - id: NOTE_001
                text: "비정규화 count는 의도적 결정"
                scored: false
            """;

    @Test
    @DisplayName("enforced_by=judge 규칙만 추린다 (test/archunit/ci 제외)")
    void picksOnlyJudgeRules() {
        RuleCatalog catalog = RuleCatalog.fromYaml(YAML);

        assertThat(catalog.judgeRules())
                .extracting(RuleCatalog.JudgeRule::id)
                .containsExactlyInAnyOrder("CONV_001", "ARCH_003a");
    }

    @Test
    @DisplayName("프롬프트에 judge 규칙은 포함, 결정론 규칙은 제외, NOTE는 '위반 아님'으로 포함")
    void buildsPolicyFromCatalog() {
        String policy = new JudgePromptBuilder().buildPolicy(RuleCatalog.fromYaml(YAML));

        assertThat(policy).contains("CONV_001", "ARCH_003a");           // judge 규칙 포함
        assertThat(policy).doesNotContain("SEC_001", "ARCH_001", "MIG_001"); // 결정론 규칙 제외
        assertThat(policy).contains("위반이 아니다", "NOTE_001");        // NOTE 인지
    }

    @Test
    @DisplayName("실제 review-loop/rules.yaml을 파싱하면 judge 규칙 3개가 나온다")
    void parsesRealRulesFile() throws IOException {
        Path rulesFile = Path.of("review-loop/rules.yaml");
        assertThat(Files.exists(rulesFile)).as("rules.yaml SSOT 존재").isTrue();

        RuleCatalog catalog = RuleCatalog.fromFile(rulesFile);

        assertThat(catalog.judgeRules())
                .extracting(RuleCatalog.JudgeRule::id)
                .containsExactlyInAnyOrder("CONV_001", "PERF_001", "ARCH_003a");
    }
}
