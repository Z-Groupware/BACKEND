package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 도메인별 규칙 태깅 검증(key-free) — forDomain은 common + 해당 도메인 규칙만 남긴다. */
class RuleCatalogDomainTest {

    private static final String YAML = """
            rules:
              - id: CONV_001
                text: "유틸 재발명 금지"
                severity: MINOR
                enforced_by: judge
                domain: common
              - id: PERF_001
                text: "N+1 금지"
                severity: MINOR
                enforced_by: judge
                domain: common
              - id: PAY_JUDGE_1
                text: "결제 금액 계산에 부동소수점 금지"
                severity: MINOR
                enforced_by: judge
                domain: payment
              - id: QUIZ_JUDGE_1
                text: "퀴즈 채점 경계값 처리"
                severity: MINOR
                enforced_by: judge
                domain: quiz
            """;

    @Test
    @DisplayName("domain 미지정이면 common으로 파싱된다")
    void defaultsToCommon() {
        RuleCatalog c = RuleCatalog.fromYaml("""
                rules:
                  - id: CONV_001
                    text: t
                    severity: MINOR
                    enforced_by: judge
                """);
        assertThat(c.judgeRules().get(0).domain()).isEqualTo("common");
    }

    @Test
    @DisplayName("forDomain(payment) → common + payment만 (quiz 제외)")
    void filtersToDomainPlusCommon() {
        RuleCatalog payment = RuleCatalog.fromYaml(YAML).forDomain("payment");

        assertThat(payment.judgeRules()).extracting(RuleCatalog.JudgeRule::id)
                .containsExactlyInAnyOrder("CONV_001", "PERF_001", "PAY_JUDGE_1");
        assertThat(payment.judgeRules()).extracting(RuleCatalog.JudgeRule::id)
                .doesNotContain("QUIZ_JUDGE_1");
    }

    @Test
    @DisplayName("전체(forDomain 미적용)는 모든 judge 규칙")
    void allWhenNotFiltered() {
        assertThat(RuleCatalog.fromYaml(YAML).judgeRules()).hasSize(4);
    }
}
