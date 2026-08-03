package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Learning Loop 배선 E2E — 쓰기 끝(교훈 기록)과 읽기 끝(다음 판정이 반영)이 실제 경로로 이어지는지.
 * 부품(KnowledgeStore·JudgePromptBuilder)은 이미 테스트돼 있다. 여기선 "ReviewLoop이 그걸 쓰는지"만 본다.
 */
class KnowledgeLoopWiringTest {

    private static final String YAML = """
            rules:
              - id: CONV_001
                text: "유틸 재발명 금지"
                severity: MINOR
                enforced_by: judge
            """;

    @TempDir
    Path repoRoot;

    private ReviewLoop loopWith(List<Lesson> lessons, AtomicReference<String> policySink) {
        LlmJudgePort stub = (file, code, policy) -> {
            policySink.set(policy);          // 판정에 전달된 정책을 포착
            return List.of();
        };
        return new ReviewLoop(
                stub,
                RuleCatalog.fromYaml(YAML),
                new EvidenceValidator(repoRoot),
                new JudgeScorer(Map.of("CONV_001", 15),
                        Map.of(Severity.MINOR, 10, Severity.CRITICAL, 40),
                        JudgeScorer.DEFAULT_PASS_THRESHOLD),
                lessons);
    }

    @Test
    @DisplayName("기록한 교훈이 다음 판정 프롬프트에 실린다 (쓰기→읽기 루프가 닫힌다)")
    void recordedLessonReachesNextJudgePrompt() throws IOException {
        // 쓰기 끝: 사람이 CONV_001을 오탐으로 판정 → KnowledgeStore에 기록
        Path lessonsFile = repoRoot.resolve("lessons.jsonl");
        KnowledgeStore store = new KnowledgeStore(lessonsFile);
        store.record(new Lesson("2026-07-18T10:00:00", "CONV_001", LessonKind.FALSE_POSITIVE,
                "재사용할 유틸이 없어 오탐 — flag 금지"));

        // 읽기 끝: 다음 라운드가 축적된 교훈을 로드해 판정
        AtomicReference<String> policy = new AtomicReference<>();
        loopWith(store.lessons(), policy).review("Svc.java", "the code");

        assertThat(policy.get())
                .contains("과거 사람 검토에서 나온 교훈")
                .contains("[FALSE_POSITIVE] CONV_001")
                .contains("재사용할 유틸이 없어 오탐");
    }

    @Test
    @DisplayName("CONFIRMED는 프롬프트에 안 들어간다 (Judge의 실수가 아니라 정확도 집계용)")
    void confirmedIsNotInjectedIntoPrompt() {
        AtomicReference<String> policy = new AtomicReference<>();
        loopWith(List.of(new Lesson("t", "CONV_001", LessonKind.CONFIRMED, "실제 중복이라 확정")), policy)
                .review("Svc.java", "the code");

        // CONFIRMED만 있으면 "같은 실수를 반복하지 마라" 섹션 자체가 없어야 한다
        assertThat(policy.get()).doesNotContain("과거 사람 검토에서 나온 교훈");
        assertThat(policy.get()).doesNotContain("실제 중복이라 확정");
    }

    @Test
    @DisplayName("교훈이 없으면 프롬프트에 교훈 섹션이 없다 (첫 라운드 회귀 방지)")
    void noLessonsMeansNoLessonSection() {
        AtomicReference<String> policy = new AtomicReference<>();
        loopWith(List.of(), policy).review("Svc.java", "the code");

        assertThat(policy.get())
                .contains("CONV_001")                          // 규칙은 여전히 실림
                .doesNotContain("과거 사람 검토에서 나온 교훈");   // 교훈 섹션은 없음
    }
}
