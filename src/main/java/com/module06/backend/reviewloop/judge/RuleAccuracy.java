package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 규칙 정확도 — 사람 판정(교훈)을 규칙별로 집계해 오탐률을 낸다.
 * Learning Loop의 마지막 노드(Rule Accuracy → Prompt 개선): 오탐률 높은 규칙 = 프롬프트 손볼 후보.
 *
 * <p>오탐률 = FALSE_POSITIVE ÷ (FALSE_POSITIVE + CONFIRMED)
 * = "Judge가 떴고 사람이 판정한 것 중, 오탐이었던 비율". 분자·분모 모두 공유되는 knowledge/에서 나오므로
 * 팀 차원의 실제 비율이다(개별 실행 수집=telemetry 불필요). MISSED는 오탐률과 무관(recall 지표)이라 분리 표기.
 *
 * <p>실행: {@code ./gradlew reviewAccuracy}
 */
public final class RuleAccuracy {

    /** 규칙 하나의 판정 집계. */
    public record Stat(String ruleId, long falsePositives, long confirmed, long missed) {
        /** Judge가 떴고 사람이 판정한 횟수(오탐률의 분모). */
        public long reviewed() {
            return falsePositives + confirmed;
        }

        /** 오탐률 — 판정된 게 없으면 정의되지 않음(-1). */
        public double falsePositiveRate() {
            return reviewed() == 0 ? -1 : (double) falsePositives / reviewed();
        }
    }

    /** 판정을 규칙별로 집계 — 오탐 많은 순, 동수면 ruleId 순. */
    static List<Stat> summarize(List<Lesson> lessons) {
        Map<String, long[]> byRule = new LinkedHashMap<>();   // ruleId -> [fp, confirmed, missed]
        for (Lesson l : lessons) {
            long[] c = byRule.computeIfAbsent(l.ruleId(), k -> new long[3]);
            switch (l.kind()) {
                case FALSE_POSITIVE -> c[0]++;
                case CONFIRMED -> c[1]++;
                case MISSED -> c[2]++;
            }
        }
        List<Stat> stats = new ArrayList<>();
        byRule.forEach((rule, c) -> stats.add(new Stat(rule, c[0], c[1], c[2])));
        stats.sort(Comparator.comparingLong(Stat::falsePositives).reversed()
                .thenComparing(Stat::ruleId));
        return stats;
    }

    /** 사람이 읽는 표. */
    static String render(List<Stat> stats) {
        if (stats.isEmpty()) {
            // "신호 없음"으로 끝내면 학습 루프가 열린 채 방치된다 — 실제로 오래 0건이었다.
            // 읽기(프롬프트 주입)는 배선돼 있고 쓰기만 사람 손이므로, 여기서 무엇을 해야 하는지 알려준다.
            return """
                   규칙 정확도: 축적된 판정 0건 — ⚠️ 학습 루프가 닫히지 않았다.

                   판정 프롬프트 개선은 사람의 수락/오탐 판정에서만 나온다(읽기는 이미 자동 주입).
                   diff를 승인·되돌릴 때마다 기록할 것 — 수정 요청서에 복붙용 명령이 들어 있다:
                     ./gradlew reviewLesson --args="--rule <RULE> --kind CONFIRMED      --note '무엇을 고쳤는지'"
                     ./gradlew reviewLesson --args="--rule <RULE> --kind FALSE_POSITIVE --note '왜 오탐인지'"
                   절차: review-loop/DRIVER.md 8번 단계
                   """;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("규칙 정확도 (사람 판정 기반 · 오탐 많은 순)\n");
        sb.append(String.format("%-14s %6s %9s %7s %9s%n", "RULE", "오탐", "CONFIRMED", "MISSED", "오탐률"));
        for (Stat s : stats) {
            String rate = s.falsePositiveRate() < 0
                    ? "-"
                    : String.format("%.0f%% (n=%d)", s.falsePositiveRate() * 100, s.reviewed());
            sb.append(String.format("%-14s %6d %9d %7d %9s%n",
                    s.ruleId(), s.falsePositives(), s.confirmed(), s.missed(), rate));
        }
        sb.append("\n오탐률 = 오탐 ÷ (오탐+CONFIRMED). 높은 규칙 = 판정 프롬프트를 손볼 후보(false positive를 줄이도록).\n");
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        List<Lesson> lessons = new KnowledgeStore(ReviewLoopPaths.LESSONS).lessons();
        System.out.print(render(summarize(lessons)));
    }

    private RuleAccuracy() {
    }
}
