package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * ★ 판정(찾기) 단일 진입점 — 두 가지 트리거로 같은 루프 코어를 돌린다(재사용).
 *
 * 통합 설계(review-loop/UNIFIED_DESIGN.md) 이후 <b>저장소의 모든 LLM 판정은 이 러너를 지난다.</b>
 * 자율 경로의 판정 전용 모드(구 {@code reviewAutoFix --dry-run})는 여기로 흡수됐다 — 판정기는 하나다.
 * 수정은 이 러너가 하지 않는다(드라이버=Claude Code). 검증은 scripts/review-verify.sh.
 *
 *  1) 도메인 스캔(수동): ./gradlew reviewLoop --args="--path src/.../domain/cart --domain cart --max 3"
 *  2) 변경 파일 게이트(pre-push 훅): ./gradlew reviewLoop --args="--files-from build/changed.txt --gate"
 *
 *   --path        디렉토리 스캔 (수동 리뷰용). --max로 파일 수 상한(비용 방어).
 *   --files-from  변경 파일 목록 파일(한 줄에 하나) — pre-push 훅이 diff로 만들어 넘긴다.
 *   --domain      해당 도메인 규칙만 적용(common + 도메인).
 *   --rules       규칙 카탈로그(기본 review-loop/rules.yaml).
 *   --gate        판정을 종료코드로 — 차단 결정(미완성/Critical)이 하나라도 있으면 exit 1.
 *   --status-out  게이트 결과를 한 줄로 기록(기본 build/reviewloop-status.txt) — 아래 참조.
 *
 * 코어(ReviewLoop 등)는 도메인·트리거 무관 — 여기선 대상·규칙만 주입한다.
 * GEMINI_API_KEY가 없으면 Gate 2(LLM 판정)는 생략하고 통과 처리한다(로컬 게이트는 훅의 Gate 1이 담당).
 *
 * <h2>결과 신호 — "코드가 나쁨"과 "리뷰가 안 돌았음"은 다르다</h2>
 * 예전에는 예외가 그대로 터져 나가 종료코드가 1이 됐고, 훅이 그것을 <b>"Critical 차단"으로 오보고</b>했다.
 * 판정이 시작조차 못 한 것과 코드에 문제가 있는 것이 같은 메시지로 보였다 —
 * 실제로 이 저장소에서 키 형식 오류가 그렇게 가려졌다(감사 로그 0건인데 아무도 몰랐다).
 * 그래서 결과를 <b>상태 파일 한 줄</b>로 명시한다({@link #STATUS_OK}/{@link #STATUS_BLOCKED}/{@link #STATUS_ERROR}):
 * <ul>
 *   <li>{@code OK}      — 판정 완료(통과) 또는 대상·키 없음으로 생략. exit 0
 *   <li>{@code BLOCKED} — 판정 완료 + 차단 결정(Critical/미완성). exit 1 ← <b>코드 판정</b>
 *   <li>{@code ERROR}   — 판정 실패(LLM 오류·키 형식·네트워크 등). exit 2 ← <b>리뷰 미수행</b>
 * </ul>
 * gradle이 JavaExec의 종료코드를 1로 뭉개므로 <b>훅·CI는 종료코드가 아니라 이 파일로 구분</b>해야 한다.
 * 파일이 비어 있으면 러너가 시작조차 못 한 것이다(컴파일 실패 등) — 그것도 '리뷰 미수행'이다.
 */
public final class ReviewLoopRunner {

    /** 판정 완료·통과 또는 생략. */
    static final String STATUS_OK = "OK";
    /** 판정 완료 + 차단 결정 — 코드 판정 결과. */
    static final String STATUS_BLOCKED = "BLOCKED";
    /** 판정 자체가 실패 — 리뷰가 수행되지 않았다. 코드 판정이 아니다. */
    static final String STATUS_ERROR = "ERROR";

    private static final String DEFAULT_STATUS_OUT = "build/reviewloop-status.txt";

    public static void main(String[] args) {
        String statusOut = CliArgs.value(args, "--status-out", DEFAULT_STATUS_OUT);
        try {
            String status = run(args);
            writeStatus(statusOut, status);
            if (STATUS_BLOCKED.equals(status)) {
                System.out.println("[GATE] 차단 결정(미완성/Critical) 발견 → exit 1. "
                        + "Minor·score<80은 통과. 우회: git push --no-verify");
                System.exit(1);
            }
            if (STATUS_ERROR.equals(status)) {
                // 예외 없이 ERROR가 나오는 경로(=사용법 오류). 스택은 없으니 찍지 않는다.
                System.out.println("[GATE] ⚠️ 게이트 오류 — 리뷰가 수행되지 않았다(코드 판정 아님) → exit 2.");
                System.exit(2);
            }
        } catch (Throwable t) {
            // 판정 실패 = 리뷰 미수행. 코드 판정(BLOCKED)과 절대 섞이면 안 된다.
            writeStatus(statusOut, STATUS_ERROR);
            System.out.println("[GATE] ⚠️ 게이트 오류 — 리뷰가 수행되지 않았다(코드 판정 아님): "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            System.out.println("[GATE] 흔한 원인: GEMINI_API_KEY 형식(개행·공백)·네트워크·API 쿼터. 아래 스택 참조.");
            t.printStackTrace(System.err);
            System.exit(2);
        }
    }

    /** 진입점 기본 배선 — 시계는 여기서 한 번 만들어 아래로 넘긴다. */
    static String run(String[] args) throws Exception {   // 테스트가 상태 분류를 직접 검증한다
        return run(args, Clock.systemUTC());
    }

    /**
     * @param clock 감사 로그 타임스탬프의 출처. 형제 러너({@link ReviewRunner}·{@link AutoFixRunner})와 같은 규약 —
     *              시각을 주입받아야 기록을 고정할 수 있고, CONV_001(표준 유틸 우회 금지)도 지켜진다.
     *              맨몸 {@code LocalDateTime.now()}를 쓰다가 이 루프 자신의 Gate 2에 걸려 고친 자리다.
     * @return {@link #STATUS_OK}/{@link #STATUS_BLOCKED}/{@link #STATUS_ERROR}.
     *         판정 도중의 실패는 예외로 던진다(main이 ERROR로 분류) — 여기서 ERROR를 직접 반환하는 건
     *         예외가 아닌 '리뷰 미수행' 경로(사용법 오류)뿐이다.
     */
    static String run(String[] args, Clock clock) throws Exception {
        String filesFrom = CliArgs.value(args, "--files-from", null);
        String path = CliArgs.value(args, "--path", null);
        String domain = CliArgs.value(args, "--domain", null);
        String rulesPath = CliArgs.value(args, "--rules", "review-loop/rules.yaml");
        int max = Integer.parseInt(CliArgs.value(args, "--max", "5"));
        boolean gate = CliArgs.flag(args, "--gate");
        String findingsOut = CliArgs.value(args, "--findings-out", null);   // Minor findings를 파일로(자동수정기 입력)

        List<Path> targets = resolveTargets(filesFrom, path, max);
        if (targets == null) {
            // 대상 지정이 없다 = CLI 사용법 오류. 이건 '통과'가 아니라 '리뷰 미수행'이다 —
            // OK로 기록하면 훅·CI 인자 구성이 바뀌었을 때 판정을 안 하고도 초록이 된다(이 러너가 막으려는 바로 그 실패).
            System.out.println("사용법: --args=\"(--path <dir> | --files-from <list>) [--domain X] [--max N] [--gate]\"");
            resetFindings(findingsOut);
            return STATUS_ERROR;
        }
        if (targets.isEmpty()) {
            // 목록은 정상인데 리뷰할 .java가 없다 = 판정할 것이 없음. 통과가 맞다(사용법 오류와 다르다).
            System.out.println("리뷰할 .java 파일이 없습니다 → 통과.");
            resetFindings(findingsOut);
            return STATUS_OK;
        }

        if (!ApiKeys.present(System.getenv("GEMINI_API_KEY"))) {
            System.out.println("GEMINI_API_KEY 없음 → Gate 2(LLM 판정) 생략"
                    + (gate ? " · 게이트 통과 처리(Gate 1은 별도)" : ""));
            resetFindings(findingsOut);
            return STATUS_OK;   // 키 부재로 push를 막지 않는다(의도된 정책)
        }

        RuleCatalog catalog = RuleCatalog.fromFile(Path.of(rulesPath));
        if (domain != null) {
            catalog = catalog.forDomain(domain);
        }
        LlmJudgePort judge = new GeminiJudgeAdapter();
        // 가중치·임계값은 rules.yaml(meta.score)이 SSOT — 코드에 하드코딩하지 않는다.
        // 새 enforced_by:judge 규칙을 yaml에 추가하면 자동으로 judge_default_weight를 받는다.
        JudgeScorer scorer = new JudgeScorer(
                catalog.effectiveWeights(),
                catalog.scorePolicy().defaultWeightBySeverity(),
                catalog.scorePolicy().passThreshold());

        Files.createDirectories(ReviewLoopPaths.AUDIT_LOG.getParent());
        AuditLogWriter audit = new AuditLogWriter(ReviewLoopPaths.AUDIT_LOG);

        // Learning Loop 읽기 끝 — 축적된 사람 교훈을 판정 프롬프트에 반영(없으면 빈 리스트).
        List<Lesson> lessons = new KnowledgeStore(ReviewLoopPaths.LESSONS).lessons();

        List<RuleCatalog.JudgeRule> blockingRules = catalog.blockingRules();

        StringBuilder out = new StringBuilder();
        out.append("== 리뷰 루프 실행 ==\n");
        out.append("대상  : ").append(targets.size()).append("개 파일")
           .append(gate ? " · 게이트 모드" : "").append('\n');
        out.append("도메인: ").append(domain == null ? "(전체 규칙)" : domain).append('\n');
        out.append("규칙  : judge 규칙 ").append(catalog.judgeRules().size()).append("개")
           .append(gate ? " · 차단 가능(CRITICAL) " + blockingRules.size() + "개" : "").append('\n');

        // 학습 루프 — 0건이면 침묵하지 않는다. 읽기는 배선돼 있어도 쓸 게 없으면 루프가 닫히지 않는다.
        if (lessons.isEmpty()) {
            out.append("교훈  : 0건 — 학습 루프 미가동(판정 프롬프트에 반영할 사람 판정이 없다)\n");
            out.append("        수락/되돌림을 결정할 때마다 기록할 것: ./gradlew reviewLesson"
                    + " --args=\"--rule <RULE> --kind CONFIRMED|FALSE_POSITIVE --note '<근거>'\"\n");
        } else {
            out.append("교훈  : 축적 ").append(lessons.size()).append("건 반영\n");
        }

        // Gate 2의 역할을 매 실행에 명시한다 — "게이트가 지켜준다"는 착각이 생기지 않게.
        // 정책(확정): Gate 2 = 리포터. 차단은 결정론 게이트(Gate 1 ArchUnit · semgrep) 몫이다.
        // 나중에 CRITICAL judge 규칙이 추가되면 아래가 차단 가능 개수를 알려준다(문구가 저절로 맞는다).
        if (gate) {
            out.append(blockingRules.isEmpty()
                    ? "역할  : 리포터 — 차단 규칙 0개. Gate 2는 push를 막지 않는다(차단은 Gate 1·semgrep · DRIVER.md)\n"
                    : "역할  : 차단 게이트 — CRITICAL 규칙 " + blockingRules.size() + "개가 차단을 만들 수 있다\n");
        }
        out.append('\n');

        int round = 0;
        boolean blocked = false;
        List<String> minorFindings = new ArrayList<>();   // 자동수정 대상(Minor만) — Claude Code에 넘김
        for (Path f : targets) {
            String code = Files.readString(f);
            ReviewLoop loop = new ReviewLoop(judge, catalog, evidenceFor(f), scorer, lessons);
            JudgeVerdict v = loop.review(f.getFileName().toString(), code);

            if (isBlocking(v.decision())) {
                blocked = true;
            }
            out.append(mark(v.decision())).append(' ').append(f.getFileName())
               .append("  → score ").append(v.score()).append(" · ").append(v.decision())
               .append(" · findings ").append(v.findings().size()).append('\n');
            for (Finding fd : v.findings()) {
                out.append("    - ").append(fd.ruleId()).append(" (").append(fd.severity()).append("): ")
                   .append(fd.description()).append(" [").append(fd.file()).append(':').append(fd.line()).append("]\n");
                if (fd.severity() == Severity.MINOR) {   // 자동수정 대상 — Critical은 제외(사람)
                    minorFindings.add(toPosixPath(f) + ":" + fd.line()
                            + " [" + fd.ruleId() + "] " + fd.description());
                }
            }
            audit.append(new AuditRecord(LocalDateTime.now(clock).toString(), ++round, "gemini",
                    v.score(), v.hasCritical(), v.decision(), v.findings().size(), false));
        }
        out.append("\n감사 로그: ").append(ReviewLoopPaths.AUDIT_LOG).append(" (누적)\n");

        if (findingsOut != null) {   // 자동수정기(Claude Code) 입력 — Minor findings만
            writeFindings(findingsOut, minorFindings);
            out.append("자동수정 대상(Minor) ").append(minorFindings.size()).append("건 → ").append(findingsOut).append('\n');
        }

        String report = out.toString();
        System.out.println(report);
        Files.writeString(Path.of("build/reviewloop-run.txt"), report);

        return (gate && blocked) ? STATUS_BLOCKED : STATUS_OK;
    }

    /**
     * 게이트 결과를 한 줄로 기록한다 — 훅·CI가 "코드 판정"과 "리뷰 미수행"을 구분하는 유일한 신호.
     * 기록 실패가 게이트를 망가뜨리면 안 되므로(신호가 목적이지 게이트가 아니다) 삼켜서 경고만 남긴다.
     */
    static void writeStatus(String statusOut, String status) {
        try {
            Path f = Path.of(statusOut);
            if (f.getParent() != null) {
                Files.createDirectories(f.getParent());
            }
            Files.writeString(f, status + "\n");
        } catch (IOException | RuntimeException e) {
            System.out.println("[GATE] 상태 파일 기록 실패(" + statusOut + "): " + e
                    + " — 상태는 " + status);
        }
    }

    /**
     * 조기 종료 경로에서 findings 파일을 비운다 — 안 비우면 이전 실행의 findings가 남아
     * scripts/review-fix.sh가 이미 처리된(또는 무관한) 낡은 지적으로 수정 요청서를 만든다.
     */
    private static void resetFindings(String findingsOut) throws IOException {
        if (findingsOut != null) {
            writeFindings(findingsOut, List.of());
        }
    }

    /**
     * 경로 규약 — LLM에는 파일명만 주고, 근거 검증은 <b>그 파일의 부모 디렉터리</b> 기준으로 한다.
     * (통합 설계 P2: 휴면 전환된 {@link AutoLoopOrchestrator}와 같은 규약. 판정은 이 러너로 일원화.)
     *
     * <p>repo 루트 하나로 고정하면 worktree·임시 디렉터리·절대경로 목록에서 근거 검증이 어긋나
     * 실재하는 finding이 환각으로 버려진다. 부모 기준이면 상대·절대 목록 모두에서 맞는다.
     * 부모가 없는 경로(파일명만)는 CWD(".")를 기준으로 본다.
     */
    static EvidenceValidator evidenceFor(Path file) {
        Path parent = file.getParent();
        return new EvidenceValidator(parent == null ? Path.of(".") : parent);
    }

    /** 차단 결정 = 완료 하드게이트 미충족(INCOMPLETE) 또는 사람 승인 필요(AWAITING_HUMAN=Critical). */
    static boolean isBlocking(JudgeDecision d) {
        return d == JudgeDecision.INCOMPLETE || d == JudgeDecision.AWAITING_HUMAN;
    }

    /** 대상 목록 결정: --files-from 우선, 없으면 --path 스캔. 둘 다 없으면 null(사용법 출력). */
    private static List<Path> resolveTargets(String filesFrom, String path, int max) throws Exception {
        if (filesFrom != null) {
            Path listFile = Path.of(filesFrom);
            if (!Files.exists(listFile)) {   // 목록 파일 부재 → 크래시 대신 통과(리뷰 대상 없음)
                System.out.println("변경파일 목록이 없습니다: " + filesFrom + " → 통과.");
                return List.of();
            }
            List<Path> files = new ArrayList<>();
            for (String line : Files.readAllLines(listFile)) {
                String s = line.replace("﻿", "").trim();   // BOM/공백 방어
                if (s.isBlank() || !s.endsWith(".java")) {
                    continue;
                }
                Path p = Path.of(s);
                if (Files.exists(p)) {
                    files.add(p);
                }
            }
            if (files.size() > max) {   // --files-from에도 max 적용(LLM 호출 폭발·비용 방어). 조용히 자르지 않고 경고.
                System.out.println("변경 .java " + files.size() + "개 중 " + max + "개만 리뷰 · 나머지 "
                        + (files.size() - max) + "개 스킵 — 상한 조정: --max N");
                return List.copyOf(files.subList(0, max));
            }
            return files;
        }
        if (path != null) {
            try (Stream<Path> stream = Files.walk(Path.of(path))) {
                return stream.filter(p -> p.toString().endsWith(".java")).sorted().limit(max).toList();
            }
        }
        return null;
    }

    /**
     * Minor findings를 파일로 — 부모 디렉터리가 없으면 만들고 쓴다.
     * --findings-out은 외부에서 임의 경로로 주어지므로, 게이트가 자기 IO 오류(NoSuchFileException)로
     * push를 막지 않도록 방어한다. findings 0건이어도 빈 파일을 남긴다(소비자가 존재를 가정).
     */
    static void writeFindings(String findingsOut, List<String> minorFindings) throws IOException {
        Path findingsFile = Path.of(findingsOut);
        Path findingsDir = findingsFile.getParent();   // 파일명만 준 경우 null → createDirectories(null)은 NPE
        if (findingsDir != null) {
            Files.createDirectories(findingsDir);
        }
        Files.writeString(findingsFile,
                minorFindings.isEmpty() ? "" : String.join("\n", minorFindings) + "\n");
    }

    /** findings 경로는 OS 무관하게 '/' — Windows의 '\'가 Linux CI 출력·마크다운 요청서와 갈리는 것 방지. */
    static String toPosixPath(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String mark(JudgeDecision d) {
        return switch (d) {
            case PASS -> "[OK ]";
            case NEEDS_REVISION -> "[FIX]";
            case AWAITING_HUMAN -> "[HUM]";
            case INCOMPLETE -> "[INC]";
        };
    }


    private ReviewLoopRunner() {
    }
}
