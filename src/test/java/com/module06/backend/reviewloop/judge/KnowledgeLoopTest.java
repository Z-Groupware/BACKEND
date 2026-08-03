package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지식 축적 루프 검증(핵심) — 사람 정정이 축적되고, 다음 프롬프트에 주입되어 재발 방지된다.
 * "AI가 초안 → 사람 검증 → 그 검증이 다음 AI의 학습 데이터" (HITL). LLM 없이 결정론 검증.
 */
class KnowledgeLoopTest {

    private static final String YAML = """
            rules:
              - id: CONV_001
                text: "유틸 재발명 금지"
                severity: MINOR
                enforced_by: judge
              - id: ARCH_003a
                text: "타 도메인 JpaEntity 투영 예외"
                severity: MINOR
                enforced_by: judge
            """;

    @TempDir
    Path dir;

    @Test
    @DisplayName("교훈은 append-only로 쌓이고 그대로 읽힌다")
    void lessonsAccumulate() throws IOException {
        KnowledgeStore store = new KnowledgeStore(dir.resolve("lessons.jsonl"));

        store.record(new Lesson("2026-07-14T00:00:00", "ARCH_003a", LessonKind.FALSE_POSITIVE,
                "투영(read model)은 예외인데 위반으로 오판함 — flag 금지"));
        store.record(new Lesson("2026-07-14T00:01:00", "PERF_001", LessonKind.MISSED,
                "상위 서비스의 루프 내 조회를 놓침 — 호출부까지 함께 볼 것"));

        List<Lesson> lessons = store.lessons();
        assertThat(lessons).hasSize(2);
        assertThat(lessons.get(0).kind()).isEqualTo(LessonKind.FALSE_POSITIVE);
        assertThat(lessons.get(1).ruleId()).isEqualTo("PERF_001");
    }

    @Test
    @DisplayName("축적된 교훈이 다음 Judge 프롬프트에 주입된다(재발 방지)")
    void lessonsAreInjectedIntoPrompt() {
        List<Lesson> lessons = List.of(
                new Lesson("t", "ARCH_003a", LessonKind.FALSE_POSITIVE, "투영은 예외 — flag 금지"));

        String policy = new JudgePromptBuilder().buildPolicy(RuleCatalog.fromYaml(YAML), lessons);

        assertThat(policy).contains("과거 사람 검토에서 나온 교훈");
        assertThat(policy).contains("FALSE_POSITIVE", "투영은 예외");   // 사람 정정이 프롬프트에 들어감
    }

    @Test
    @DisplayName("HITL 폐루프: 사람이 오판을 정정→기록→다음 프롬프트가 그 교훈을 반영")
    void closedHumanInTheLoop() throws IOException {
        KnowledgeStore store = new KnowledgeStore(dir.resolve("k.jsonl"));

        // 1) 사람이 Judge의 ARCH_003a 오판을 정정하여 기록
        store.record(new Lesson("t", "ARCH_003a", LessonKind.FALSE_POSITIVE, "투영은 예외 — 반려"));

        // 2) 다음 라운드 프롬프트는 그 교훈을 담는다
        String nextPolicy = new JudgePromptBuilder().buildPolicy(RuleCatalog.fromYaml(YAML), store.lessons());

        assertThat(nextPolicy).contains("ARCH_003a", "반려");
    }
}
