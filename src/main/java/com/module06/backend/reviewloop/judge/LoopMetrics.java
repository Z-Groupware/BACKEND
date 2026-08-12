package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ★ 최적화 루프 — <b>루프가 자기 자신을 측정한다.</b>
 *
 * <p>여태 이 저장소의 계측은 전부 <b>코드</b>를 향해 있었다: {@link ReviewReport}는 라운드를 나열하고,
 * {@link RuleAccuracy}는 규칙의 오탐률을 내고, {@code review-score-domains.sh}는 도메인 점수를 랭크한다.
 * 그런데 <b>루프 자체가 잘 돌고 있는지</b>를 답하는 것은 없었다 — LLM 판정 한 번이 얼마나 신호를 내는지,
 * 변경이 리뷰를 얼마나 받는지, 나온 신호가 학습으로 얼마나 전환되는지. 측정하지 않는 것은 개선되지 않는다.
 *
 * <p>이 클래스가 그 세 개를 지표로 만들고, 임계값을 넘기면 <b>어느 손잡이를 돌릴지</b>까지 지목한다.
 * 자동으로 돌리지는 않는다 — 루프의 불변식(고치는 주체는 사람·드라이버)과 같은 이유다.
 *
 * <pre>
 *   ./gradlew reviewOptimize                       # 측정 + 진단
 *   ./gradlew reviewOptimize --args="--snapshot"   # 기준선에 한 줄 적재(다음 실행이 델타를 보여준다)
 * </pre>
 *
 * <h2>측정 단위 — 섞으면 안 되는 두 종류의 행</h2>
 * {@code error_log.jsonl} 한 줄은 성격이 둘이다({@link AuditRecord#isSkipped()}):
 * <ul>
 *   <li><b>판정 행</b> — 파일 1개를 LLM이 판정했다. 즉 <b>판정 행 1개 = LLM 호출 1회 = 리뷰된 .java 1개</b>.
 *   <li><b>생략 행</b> — 실행 1회가 판정 없이 통과했다. 그 행의 건수는 <b>리뷰되지 않은 .java 수</b>다.
 * </ul>
 * 그래서 커버리지의 분모는 '실행'이 아니라 <b>.java 개수</b>로 맞춘다 — 단위를 섞으면 생략 1회(파일 20개)와
 * 판정 1회(파일 1개)가 같은 무게가 되어 커버리지가 실제보다 훨씬 좋게 나온다.
 *
 * <h2>일부러 계산하지 않는 것 — '수렴 라운드'</h2>
 * {@code round} 필드는 <b>기록한 러너에 따라 뜻이 다르다.</b> {@link AutoFixRunner}에서는 진짜 수렴 라운드지만
 * {@link ReviewLoopRunner}(현 주 경로)에서는 <b>한 실행 안의 파일 순번</b>이다({@code ++round} 위치를 볼 것).
 * 그 둘이 한 파일에 섞여 있으므로 "평균 몇 라운드에 수렴하나"는 지금 데이터로 계산할 수 없다.
 * 계산하면 그럴듯한 숫자가 나오고, 그 숫자로 예산 캡을 조정하게 된다 — 그게 계측 없는 최적화보다 나쁘다.
 * 필요해지면 먼저 {@code AuditRecord}에 '무엇을 세는 round인지' 구분 필드를 넣어야 한다(계측이 선행).
 */
public final class LoopMetrics {

    /** 커버리지 하한 — 미만이면 "변경이 리뷰를 못 받고 있다". 나머지 지표보다 먼저 고칠 것. */
    static final double COVERAGE_FLOOR = 0.90;
    /** 판정 수율 하한 — 미만이면 LLM 호출 대부분이 빈손이다(대상 선정 또는 규칙 문제). */
    static final double YIELD_FLOOR = 0.20;
    /** 학습 전환율 하한 — 미만이면 신호가 나왔는데 기억으로 남지 않았다(DRIVER.md 8번 누락). */
    static final double CLOSURE_FLOOR = 0.50;
    /** 규칙별 오탐률 상한 — 초과 규칙은 프롬프트·규칙 문안 손질 후보. */
    static final double FP_RATE_CEIL = 0.50;

    /** 지표 기준선 — 추적(커밋) 대상. 판정 결과가 아니라 '루프의 상태'라 knowledge/에 둔다. */
    static final Path BASELINE = Path.of("review-loop/knowledge/loop-metrics.jsonl");

    /**
     * 한 시점의 루프 상태. <b>원시 카운터만</b> 담는다 — 비율은 읽을 때 계산한다.
     * 비율까지 적어 두면 정의를 바꿀 때 과거 줄과 새 줄의 뜻이 달라지고, 그 차이는 파일만 봐서는 안 보인다.
     *
     * <p>파생 접근자에 {@code @JsonIgnore}가 붙은 이유는 {@link AuditRecord#isSkipped()}와 같다 —
     * 없으면 Jackson이 계산값을 속성으로 써넣고, 그 줄을 다시 읽을 때 미지 속성이 된다.
     */
    record Snapshot(
            String timestamp,
            long judged,            // 판정 행 = 리뷰된 .java = LLM 호출 수
            long withFinding,       // 그중 finding을 1건 이상 낸 판정
            long findings,          // finding 총건수
            long belowThreshold,    // score < pass_threshold 인 판정
            long skipRuns,          // 생략 실행 횟수
            long unreviewed,        // 리뷰되지 않은 .java 누적(셀 수 있었던 것만)
            long uncountableSkips,  // 건수를 셀 수 없었던 생략(대상 확정 전 실패)
            long humanJudged,       // 사람이 판정한 finding 수(FALSE_POSITIVE + CONFIRMED)
            long falsePositives
    ) {

        /** 리뷰된 .java ÷ (리뷰된 + 리뷰되지 않은). 분모 0이면 정의되지 않음(-1). */
        @JsonIgnore
        double coverage() {
            long total = judged + unreviewed;
            return total == 0 ? -1 : (double) judged / total;
        }

        /** finding을 낸 판정 ÷ 전체 판정 — LLM 호출 1회가 신호를 낼 확률. */
        @JsonIgnore
        double yield() {
            return judged == 0 ? -1 : (double) withFinding / judged;
        }

        /** 사람이 판정한 finding ÷ 전체 finding — 나온 신호가 기억으로 남은 비율. */
        @JsonIgnore
        double closure() {
            return findings == 0 ? -1 : (double) humanJudged / findings;
        }
    }

    /** 감사 로그 + 교훈을 한 시점의 지표로 접는다. */
    static Snapshot measure(String timestamp, List<AuditRecord> records, List<Lesson> lessons, int passThreshold) {
        long judged = 0;
        long withFinding = 0;
        long findings = 0;
        long belowThreshold = 0;
        long skipRuns = 0;
        long unreviewed = 0;
        long uncountableSkips = 0;

        for (AuditRecord r : records) {
            if (r.isSkipped()) {
                skipRuns++;
                // UNKNOWN_COUNT(-1)를 그냥 더하면 누적이 줄어든다 — 모르는 것은 0으로 세지 않고 따로 센다.
                if (r.unreviewedFiles() < 0) {
                    uncountableSkips++;
                } else {
                    unreviewed += r.unreviewedFiles();
                }
                continue;
            }
            judged++;
            findings += r.findingsCount();
            if (r.findingsCount() > 0) {
                withFinding++;
            }
            if (r.score() < passThreshold) {
                belowThreshold++;
            }
        }

        long humanJudged = 0;
        long falsePositives = 0;
        for (RuleAccuracy.Stat s : RuleAccuracy.summarize(lessons)) {   // 집계는 RuleAccuracy가 SSOT
            humanJudged += s.reviewed();
            falsePositives += s.falsePositives();
        }

        return new Snapshot(timestamp, judged, withFinding, findings, belowThreshold,
                skipRuns, unreviewed, uncountableSkips, humanJudged, falsePositives);
    }

    /**
     * 사람이 읽는 진단표. {@code previous}가 null이면 델타 칸을 비운다(첫 실행).
     * 임계 위반은 반드시 <b>돌릴 손잡이</b>와 함께 적는다 — 숫자만 보여주는 리포트는 아무것도 바꾸지 않는다.
     */
    static String render(Snapshot now, Snapshot previous, List<RuleAccuracy.Stat> ruleStats, int passThreshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("최적화 루프 — 루프 자신의 지표 (감사 로그 ")
          .append(now.judged() + now.skipRuns()).append("행 기준 · 팀 누적)\n\n");

        sb.append(String.format("%-14s %10s %10s %10s%n", "지표", "현재", "이전", "기준"));
        sb.append(row("커버리지", now.coverage(), previous == null ? null : previous.coverage(), COVERAGE_FLOOR));
        sb.append(row("판정 수율", now.yield(), previous == null ? null : previous.yield(), YIELD_FLOOR));
        sb.append(row("학습 전환율", now.closure(), previous == null ? null : previous.closure(), CLOSURE_FLOOR));

        sb.append("\n원시 카운터\n");
        sb.append("  리뷰된 .java(=LLM 판정 호출) : ").append(now.judged()).append('\n');
        sb.append("  그중 finding을 낸 판정       : ").append(now.withFinding())
          .append(" (finding 총 ").append(now.findings()).append("건 · score<")
          .append(passThreshold).append(" 판정 ").append(now.belowThreshold()).append("건)\n");
        sb.append("  생략 실행                    : ").append(now.skipRuns())
          .append("회 · 리뷰되지 않은 .java ").append(now.unreviewed()).append("개");
        if (now.uncountableSkips() > 0) {
            sb.append(" (+ 건수를 셀 수 없는 생략 ").append(now.uncountableSkips()).append("회)");
        }
        sb.append('\n');
        sb.append("  사람이 판정한 finding        : ").append(now.humanJudged())
          .append("건 (오탐 ").append(now.falsePositives()).append("건)\n");

        sb.append("\n진단 · 돌릴 손잡이\n");
        List<String> actions = actions(now, ruleStats);
        if (actions.isEmpty()) {
            sb.append("  기준을 모두 충족 — 이번 주기에 조정할 손잡이가 없다.\n");
        } else {
            for (String a : actions) {
                sb.append("  ").append(a).append('\n');
            }
        }

        sb.append("""

                계측 한계(알고 쓸 것)
                  · '수렴 라운드'는 계산하지 않는다 — round 필드가 러너마다 뜻이 다르다(클래스 주석 참조).
                  · 규칙별 수율은 감사 로그로 낼 수 없다(행에 ruleId가 없다). 규칙 단위 신호는 교훈·fix-trail뿐.
                  · error_log.jsonl은 merge=union 팀 공유 파일이라 이 숫자는 개인이 아니라 팀 누적이다.
                """);
        return sb.toString();
    }

    /** 임계 위반 → 조치. 순서는 고정이다(커버리지가 먼저 — 리뷰를 못 받은 코드가 제일 비싸다). */
    static List<String> actions(Snapshot s, List<RuleAccuracy.Stat> ruleStats) {
        List<String> out = new ArrayList<>();

        if (s.coverage() >= 0 && s.coverage() < COVERAGE_FLOOR) {
            out.add(String.format(
                    "❶ 커버리지 %.0f%% — 변경 %d개가 판정 없이 나갔다. 생략 사유부터 볼 것: "
                    + "`./gradlew reviewKnowledgeDemo` 아닌 실제 리포트(ReviewReport '판정 미수행' 절). "
                    + "키 미주입이면 review-loop/SETUP.md §2, 쿼터면 크레딧. 소급 리뷰 대상 구간이다.",
                    s.coverage() * 100, s.unreviewed()));
        }
        if (s.yield() >= 0 && s.yield() < YIELD_FLOOR) {
            out.add(String.format(
                    "❷ 판정 수율 %.0f%% — LLM 판정 %d회 중 %d회만 신호를 냈다. 손잡이 둘: "
                    + "(a) 대상 좁히기 — judge 규칙 3개가 사는 파일 유형(Service·Repository·Query류)으로 "
                    + "`--files-from`을 걸러라(`scripts/review-score-domains.sh`의 SCOPE=core 정규식이 그 목록이다). "
                    + "(b) 규칙 늘리기 — review-loop/rules.yaml에 enforced_by: judge 추가(가중치는 yaml SSOT).",
                    s.yield() * 100, s.judged(), s.withFinding()));
        }
        if (s.closure() >= 0 && s.closure() < CLOSURE_FLOOR) {
            out.add(String.format(
                    "❸ 학습 전환율 %.0f%% — finding %d건 중 %d건만 사람 판정으로 남았다. "
                    + "나머지는 프롬프트에 반영되지 않아 같은 지적이 다시 온다. review-loop/DRIVER.md 8번: "
                    + "diff를 수락/되돌릴 때 `./gradlew reviewLesson` 1줄.",
                    s.closure() * 100, s.findings(), s.humanJudged()));
        }
        if (s.findings() > 0 && s.belowThreshold() == 0) {
            // 번호는 순번이 아니라 진단 고유 표식이다 — 앞 항목이 안 뜬다고 번호가 밀리면
            // "❷ 얘기했던 것"처럼 사람끼리 지목하던 참조가 어긋난다.
            out.add(String.format(
                    "❹ finding %d건이 나왔는데 임계 미달 판정은 0건 — 재수정 결정(NEEDS_REVISION)이 "
                    + "한 번도 나지 않았다. judge 기본 감점 15 · pass_threshold 80이면 단일 지적은 항상 "
                    + "85점 PASS다(수정 라운드 발동에 2건 필요). 의도라면 그대로 두고(요청서만 남는다), "
                    + "아니라면 rules.yaml meta.score를 조정하라 — 가중치는 yaml SSOT라 코드 수정이 없다.",
                    s.findings()));
        }
        for (RuleAccuracy.Stat st : ruleStats) {
            if (st.falsePositiveRate() > FP_RATE_CEIL) {
                out.add(String.format(
                        "❺ %s 오탐률 %.0f%% (n=%d) — rules.yaml의 text·anchor를 좁혀라. "
                        + "교훈은 프롬프트에 이미 실리지만(JudgePromptBuilder), 규칙 문안이 넓으면 계속 뜬다.",
                        st.ruleId(), st.falsePositiveRate() * 100, st.reviewed()));
            }
        }
        return out;
    }

    private static String row(String name, double now, Double previous, double floor) {
        return String.format("%-14s %10s %10s %10s%n",
                name, pct(now), previous == null ? "-" : pct(previous), String.format("≥%.0f%%", floor * 100));
    }

    private static String pct(double v) {
        return v < 0 ? "-" : String.format("%.0f%%", v * 100);
    }

    /**
     * 가장 최근 기준선. 없거나 전부 손상됐으면 null(첫 실행 취급).
     *
     * <p><b>파일의 마지막 줄이 아니라 timestamp가 가장 큰 줄</b>이다. 이 파일은 {@code merge=union}이라
     * 브랜치가 머지되면 줄 순서가 시간순이 아니다("내 쪽 먼저, 상대 쪽 나중"). 마지막 줄을 최신으로 보면
     * 머지 직후 <b>옛 지표와 비교</b>하게 되고, 개선을 악화로(또는 그 반대로) 읽는다.
     * {@link ReviewReport#records}가 감사 로그에서 같은 이유로 정렬하는 것과 같은 처방이다.
     *
     * <p>미지 속성을 무시한다 — 이 파일은 지표가 늘면 필드도 느는 이력 파일이라, 새 필드가 붙은 뒤에도
     * 과거 도구·과거 줄이 서로 읽혀야 한다. 엄격하게 읽으면 필드 추가가 이력 전체를 못 읽게 만든다.
     */
    static Snapshot lastSnapshot(Path baseline) throws IOException {
        if (!Files.exists(baseline)) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Snapshot latest = null;
        for (String line : Files.readAllLines(baseline)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Snapshot s = mapper.readValue(line, Snapshot.class);
                // timestamp 없는 줄(손상·수동 편집)은 최신 후보로 삼지 않는다 — 비교 기준이 될 수 없다.
                if (s.timestamp() != null && (latest == null || s.timestamp().compareTo(latest.timestamp()) > 0)) {
                    latest = s;
                }
            } catch (JsonProcessingException e) {
                System.out.println("[optimize] 손상된 줄 건너뜀(" + baseline + "): " + e.getOriginalMessage());
            }
        }
        return latest;
    }

    /** 기준선에 한 줄 덧붙인다(append-only — 과거 지표를 덮으면 개선 여부를 되짚을 수 없다). */
    static void appendSnapshot(Path baseline, Snapshot snapshot) throws IOException {
        if (baseline.getParent() != null) {
            Files.createDirectories(baseline.getParent());
        }
        Files.writeString(baseline, new ObjectMapper().writeValueAsString(snapshot) + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static void main(String[] args) throws IOException {
        boolean snapshot = CliArgs.flag(args, "--snapshot");
        String rulesPath = CliArgs.value(args, "--rules", "review-loop/rules.yaml");

        // 임계값은 rules.yaml(meta.score)이 SSOT. 읽을 수 없으면 문서화된 기본값으로 떨어진다 —
        // 지표 조회가 규칙 파일 문제로 실패하면 아무도 지표를 안 보게 된다.
        int passThreshold = RuleCatalog.ScorePolicy.DEFAULT.passThreshold();
        try {
            passThreshold = RuleCatalog.fromFile(Path.of(rulesPath)).scorePolicy().passThreshold();
        } catch (IOException | RuntimeException e) {
            System.out.println("[optimize] " + rulesPath + " 로드 실패 → pass_threshold 기본값 "
                    + passThreshold + " 사용: " + e);
        }

        List<AuditRecord> records = ReviewReport.records(ReviewLoopPaths.AUDIT_LOG);
        List<Lesson> lessons = new KnowledgeStore(ReviewLoopPaths.LESSONS).lessons();

        Snapshot previous = lastSnapshot(BASELINE);
        Snapshot now = measure(LocalDateTime.now(Clock.systemUTC()).toString(), records, lessons, passThreshold);

        System.out.print(render(now, previous, RuleAccuracy.summarize(lessons), passThreshold));

        if (snapshot) {
            appendSnapshot(BASELINE, now);
            System.out.println("\n기준선 적재 → " + BASELINE + " (다음 실행이 '이전' 칸에 이 값을 쓴다)");
        } else {
            System.out.println("\n기준선에 남기려면: ./gradlew reviewOptimize --args=\"--snapshot\"");
        }
    }

    private LoopMetrics() {
    }
}
