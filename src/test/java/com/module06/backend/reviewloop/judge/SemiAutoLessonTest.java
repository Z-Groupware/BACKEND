package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반자동 지식 축적 검증 — AI 초안 → 사람 승인/수정/반려, + 반복 패턴 자동 감지. (LLM은 stub)
 */
class SemiAutoLessonTest {

    @TempDir
    Path dir;

    private Finding finding(String ruleId) {
        return new Finding(ruleId, Severity.MINOR, "cat", "설명", "A.java", 10, Confidence.HIGH, FindingSource.JUDGE);
    }

    @Test
    @DisplayName("AI 초안 → 사람이 그대로 승인 → lessons.jsonl에 저장")
    void approveDraftAsIs() throws IOException {
        LessonDraftPort ai = (f, kind) -> new Lesson("(draft)", f.ruleId(), kind, "AI 초안 노트");
        KnowledgeStore store = new KnowledgeStore(dir.resolve("lessons.jsonl"));
        LessonApprovalService approval = new LessonApprovalService(store);

        Lesson draft = ai.draft(finding("ARCH_003a"), LessonKind.FALSE_POSITIVE);
        approval.approve(draft, null, "2026-07-15T10:00:00");   // 수정 없이 승인

        List<Lesson> saved = store.lessons();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).humanNote()).isEqualTo("AI 초안 노트");
        assertThat(saved.get(0).kind()).isEqualTo(LessonKind.FALSE_POSITIVE);
    }

    @Test
    @DisplayName("사람이 초안을 수정 후 승인 → 수정본이 저장")
    void approveWithEdit() throws IOException {
        LessonDraftPort ai = (f, kind) -> new Lesson("(draft)", f.ruleId(), kind, "AI 초안");
        KnowledgeStore store = new KnowledgeStore(dir.resolve("lessons.jsonl"));

        Lesson draft = ai.draft(finding("ARCH_003a"), LessonKind.FALSE_POSITIVE);
        new LessonApprovalService(store).approve(draft, "사람이 고친 노트: 투영은 예외", "2026-07-15T10:00:00");

        assertThat(store.lessons().get(0).humanNote()).isEqualTo("사람이 고친 노트: 투영은 예외");
    }

    @Test
    @DisplayName("반려하면 저장하지 않는다")
    void rejectStoresNothing() throws IOException {
        KnowledgeStore store = new KnowledgeStore(dir.resolve("lessons.jsonl"));
        LessonApprovalService approval = new LessonApprovalService(store);

        approval.reject(new Lesson("(draft)", "ARCH_003a", LessonKind.FALSE_POSITIVE, "초안"));

        assertThat(store.lessons()).isEmpty();
    }

    @Test
    @DisplayName("같은 규칙이 threshold 이상 반복되면 자동 감지(정책 리뷰 제안)")
    void detectsRepeatedPattern() {
        List<Finding> history = List.of(
                finding("ARCH_003a"), finding("ARCH_003a"), finding("ARCH_003a"), finding("ARCH_003a"),
                finding("PERF_001"), finding("CONV_001"));

        List<RepeatedPatternDetector.Alert> alerts = new RepeatedPatternDetector(3).detect(history);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).ruleId()).isEqualTo("ARCH_003a");
        assertThat(alerts.get(0).count()).isEqualTo(4);
    }
}
