package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ③ 산문 md 리포트 검증 — 라운드 기록 + 교훈을 사람이 읽는 마크다운으로. */
class ReviewReportTest {

    @Test
    @DisplayName("라운드 표 + 결과 + 교훈이 마크다운으로 렌더된다")
    void rendersMarkdown() {
        List<AuditRecord> rounds = List.of(
                new AuditRecord("t1", 1, "gemini", 75, false, JudgeDecision.NEEDS_REVISION, 1, false),
                new AuditRecord("t2", 2, "gemini", 100, false, JudgeDecision.PASS, 0, false));
        List<Lesson> lessons = List.of(
                new Lesson("t", "ARCH_003a", LessonKind.FALSE_POSITIVE, "투영은 예외 — flag 금지"));

        String md = ReviewReport.render(rounds, lessons);

        assertThat(md).contains("# AI 코드 리뷰 루프 — 리포트");
        assertThat(md).contains("| 라운드 | score | 판정 | findings | 종료사유 |");
        assertThat(md).contains("| 2 | 100 | PASS | 0 |");
        assertThat(md).contains("2라운드, 최종 PASS (score 100)");
        assertThat(md).contains("FALSE_POSITIVE", "투영은 예외");
    }

    @Test
    @DisplayName("error_log.jsonl + lessons.jsonl 파일에서 읽어 렌더 (없으면 빈 것으로)")
    void rendersFromFiles(@TempDir Path dir) throws IOException {
        Path errorLog = dir.resolve("error_log.jsonl");
        Path lessons = dir.resolve("lessons.jsonl");

        new AuditLogWriter(errorLog).append(
                new AuditRecord("t", 1, "gemini", 85, false, JudgeDecision.PASS, 1, false));
        new KnowledgeStore(lessons).record(
                new Lesson("t", "PERF_001", LessonKind.MISSED, "호출부까지 볼 것"));

        String md = ReviewReport.fromFiles(errorLog, lessons);

        assertThat(md).contains("| 1 | 85 | PASS | 1 |");
        assertThat(md).contains("MISSED", "PERF_001");
    }
}
