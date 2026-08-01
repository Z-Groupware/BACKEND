package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 지식 축적 루프의 '산문 md 생성'(아티팩트 §01 ③) — error_log(라운드 감사) + 교훈을 사람이 읽는 리포트로 렌더한다.
 * jsonl은 기계용, 이 md는 사람용(리뷰·회고·다음 sprint 정책 개선 입력).
 */
public final class ReviewReport {

    private ReviewReport() {
    }

    public static String render(List<AuditRecord> rounds, List<Lesson> lessons) {
        StringBuilder md = new StringBuilder();
        md.append("# AI 코드 리뷰 루프 — 리포트\n\n");

        md.append("## 라운드 진행\n\n");
        if (rounds.isEmpty()) {
            md.append("_기록 없음_\n");
        } else {
            md.append("| 라운드 | score | 판정 | findings | 종료사유 |\n");
            md.append("|---|---|---|---|---|\n");
            for (AuditRecord r : rounds) {
                md.append("| ").append(r.round())
                  .append(" | ").append(r.score())
                  .append(" | ").append(r.decision())
                  .append(" | ").append(r.findingsCount())
                  .append(" | ").append(r.terminatedByBudget() ? "budget 소진" : "-")
                  .append(" |\n");
            }
            AuditRecord last = rounds.get(rounds.size() - 1);
            md.append("\n**결과**: ").append(rounds.size()).append("라운드, 최종 ")
              .append(last.decision()).append(" (score ").append(last.score()).append(")");
            if (last.terminatedByBudget()) {
                md.append(" — budget 소진으로 사람 이관");
            }
            md.append('\n');
        }

        md.append("\n## 축적된 교훈 (다음 라운드 프롬프트에 반영)\n\n");
        if (lessons.isEmpty()) {
            md.append("_아직 없음_\n");
        } else {
            for (Lesson l : lessons) {
                md.append("- **[").append(l.kind()).append("]** `").append(l.ruleId())
                  .append("` — ").append(l.humanNote()).append('\n');
            }
        }
        return md.toString();
    }

    /** error_log.jsonl + lessons.jsonl을 읽어 리포트를 만든다(각 파일은 없으면 빈 것으로 취급). */
    public static String fromFiles(Path errorLog, Path lessonsLog) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<AuditRecord> rounds = new ArrayList<>();
        if (Files.exists(errorLog)) {
            for (String line : Files.readAllLines(errorLog)) {
                if (!line.isBlank()) {
                    rounds.add(mapper.readValue(line, AuditRecord.class));
                }
            }
        }
        List<Lesson> lessons = Files.exists(lessonsLog) ? new KnowledgeStore(lessonsLog).lessons() : List.of();
        return render(rounds, lessons);
    }
}
