package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판단 패널 라이브(Gemini) — 실제로 안건 3개 + 별도 추천을 생성하는지. GEMINI_API_KEY 있을 때만.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiOptionPanelLiveTest {

    @Test
    @DisplayName("Gemini가 안건 3개 + 추천을 생성한다")
    void generatesPanel() throws IOException {
        Finding finding = new Finding("ARCH_003a", Severity.MINOR, "architecture",
                "타 도메인 JpaEntity(CourseReferenceEntity)를 조회 목적으로 참조",
                "GetMyQuizzes.java", 42, Confidence.MEDIUM, FindingSource.JUDGE);

        OptionPanel panel = new GeminiOptionPanelAdapter().propose(finding);

        assertThat(panel.options()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(panel.recommendation().pick()).isNotBlank();

        // 데모: "안건 나열 + 추천 별도" 형식으로 파일에 남긴다
        StringBuilder sb = new StringBuilder();
        sb.append("finding: ").append(finding.ruleId()).append(" @ ")
          .append(finding.file()).append(':').append(finding.line()).append("\n\n[안건]\n");
        for (PanelOption o : panel.options()) {
            sb.append(" ").append(o.letter()).append(". ").append(o.title())
              .append(" (").append(o.action()).append(") — ").append(o.rationale()).append('\n');
        }
        Recommendation r = panel.recommendation();
        sb.append("\n[AI 추천 — 별도] 제가 추천하는 안건은 ").append(r.pick())
          .append("안이에요 (신뢰도 ").append(String.format("%.0f%%", r.confidence() * 100)).append(")\n")
          .append(" 이유: ").append(r.reason()).append("\n 최종 선택은 사람.\n");
        Files.writeString(Path.of("build/gemini-panel.txt"), sb.toString());
    }
}
