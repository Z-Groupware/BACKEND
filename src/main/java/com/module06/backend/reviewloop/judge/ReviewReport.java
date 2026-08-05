package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public static String render(List<AuditRecord> allRecords, List<Lesson> lessons) {
        StringBuilder md = new StringBuilder();
        md.append("# AI 코드 리뷰 루프 — 리포트\n\n");

        // 생략 행은 판정 라운드와 섞지 않는다 — 섞으면 "몇 라운드 돌았나"에 안 돈 실행이 포함되고,
        // 판정을 못 받은 사실이 표 한 줄로 묻힌다. 그걸 눈에 띄게 하는 것이 이 절의 목적이다.
        List<AuditRecord> rounds = allRecords.stream().filter(r -> !r.isSkipped()).toList();
        List<AuditRecord> skips = allRecords.stream().filter(AuditRecord::isSkipped).toList();

        renderSkips(md, skips);

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

    /**
     * "리뷰 없이 나간 건수" — 이 리포트가 답해야 하는 질문이라 맨 위에 둔다.
     *
     * <p>0건이면 아예 절을 만들지 않는다. 상시 표시되면 눈에 익어 배경이 되고, 그러면
     * 실제로 생략이 쌓였을 때도 넘어가게 된다 — 이 절은 예외일 때만 나타나야 한다.
     */
    private static void renderSkips(StringBuilder md, List<AuditRecord> skips) {
        if (skips.isEmpty()) {
            return;
        }
        int unreviewed = skips.stream().mapToInt(AuditRecord::unreviewedFiles).sum();
        md.append("## ⚠️ 판정 미수행 (Gate 2 생략)\n\n")
          .append("**").append(skips.size()).append("회 생략 · 리뷰되지 않은 `.java` 누적 ")
          .append(unreviewed).append("개**\n\n")
          .append("| 시각 | 리뷰되지 않은 .java | 사유 |\n")
          .append("|---|---|---|\n");
        for (AuditRecord r : skips) {
            md.append("| ").append(r.timestamp())
              .append(" | ").append(r.unreviewedFiles())
              .append(" | ").append(r.skipReason())
              .append(" |\n");
        }
        md.append("\n생략은 환경 문제(키·쿼터·제공자 장애)를 통과로 처리한 결과다 — "
                + "코드가 통과한 것이 아니라 판정이 없었다는 뜻이다.\n\n");
    }

    /** error_log.jsonl + lessons.jsonl을 읽어 리포트를 만든다(각 파일은 없으면 빈 것으로 취급). */
    public static String fromFiles(Path errorLog, Path lessonsLog) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<AuditRecord> records = new ArrayList<>();   // 판정 라운드 + 생략 행 — render 가 갈라 쓴다
        if (Files.exists(errorLog)) {
            for (String line : Files.readAllLines(errorLog)) {
                if (line.isBlank()) {
                    continue;
                }
                // 손상된 한 줄이 리포트 전체를 실패시키지 않도록 줄 단위로 건너뛴다(KnowledgeStore와 동일 정책).
                try {
                    records.add(mapper.readValue(line, AuditRecord.class));
                } catch (JsonProcessingException e) {
                    System.out.println("[report] 손상된 줄 건너뜀(" + errorLog + "): " + e.getOriginalMessage());
                }
            }
        }
        List<Lesson> lessons = Files.exists(lessonsLog) ? new KnowledgeStore(lessonsLog).lessons() : List.of();
        return render(records, lessons);
    }
}
