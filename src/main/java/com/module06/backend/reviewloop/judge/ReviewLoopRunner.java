package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 범용 러너 — 두 가지 진입점으로 같은 루프 코어를 돌린다(재사용).
 *
 *  1) 도메인 스캔(수동): ./gradlew reviewLoop --args="--path src/.../domain/cart --domain cart --max 3"
 *  2) 변경 파일 게이트(pre-push 훅): ./gradlew reviewLoop --args="--files-from build/changed.txt --gate"
 *
 *   --path        디렉토리 스캔 (수동 리뷰용). --max로 파일 수 상한(비용 방어).
 *   --files-from  변경 파일 목록 파일(한 줄에 하나) — pre-push 훅이 diff로 만들어 넘긴다.
 *   --domain      해당 도메인 규칙만 적용(common + 도메인).
 *   --rules       규칙 카탈로그(기본 review-loop/rules.yaml).
 *   --gate        판정을 종료코드로 — 차단 결정(미완성/Critical)이 하나라도 있으면 exit 1.
 *
 * 코어(ReviewLoop 등)는 도메인·트리거 무관 — 여기선 대상·규칙만 주입한다.
 * GEMINI_API_KEY가 없으면 Gate 2(LLM 판정)는 생략하고 통과 처리한다(로컬 게이트는 훅의 Gate 1이 담당).
 */
public final class ReviewLoopRunner {

    public static void main(String[] args) throws Exception {
        String filesFrom = CliArgs.value(args, "--files-from", null);
        String path = CliArgs.value(args, "--path", null);
        String domain = CliArgs.value(args, "--domain", null);
        String rulesPath = CliArgs.value(args, "--rules", "review-loop/rules.yaml");
        int max = Integer.parseInt(CliArgs.value(args, "--max", "5"));
        boolean gate = CliArgs.flag(args, "--gate");
        String findingsOut = CliArgs.value(args, "--findings-out", null);   // Minor findings를 파일로(자동수정기 입력)

        List<Path> targets = resolveTargets(filesFrom, path, max);
        if (targets == null) {
            System.out.println("사용법: --args=\"(--path <dir> | --files-from <list>) [--domain X] [--max N] [--gate]\"");
            return;
        }
        if (targets.isEmpty()) {
            System.out.println("리뷰할 .java 파일이 없습니다 → 통과.");
            return;   // exit 0
        }

        boolean hasKey = System.getenv("GEMINI_API_KEY") != null && !System.getenv("GEMINI_API_KEY").isBlank();
        if (!hasKey) {
            System.out.println("GEMINI_API_KEY 없음 → Gate 2(LLM 판정) 생략"
                    + (gate ? " · 게이트 통과 처리(Gate 1은 별도)" : ""));
            return;   // exit 0 — 키 부재로 push를 막지 않는다
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

        StringBuilder out = new StringBuilder();
        out.append("== 리뷰 루프 실행 ==\n");
        out.append("대상  : ").append(targets.size()).append("개 파일")
           .append(gate ? " · 게이트 모드(차단 결정 시 exit 1)" : "").append('\n');
        out.append("도메인: ").append(domain == null ? "(전체 규칙)" : domain).append('\n');
        out.append("규칙  : judge 규칙 ").append(catalog.judgeRules().size()).append("개\n");
        if (!lessons.isEmpty()) {
            out.append("교훈  : 축적 ").append(lessons.size()).append("건 반영\n");
        }
        out.append('\n');

        int round = 0;
        boolean blocked = false;
        List<String> minorFindings = new ArrayList<>();   // 자동수정 대상(Minor만) — Claude Code에 넘김
        for (Path f : targets) {
            String code = Files.readString(f);
            Path parent = f.getParent() == null ? Path.of(".") : f.getParent();
            ReviewLoop loop = new ReviewLoop(judge, catalog, new EvidenceValidator(parent), scorer, lessons);
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
            audit.append(new AuditRecord(LocalDateTime.now().toString(), ++round, "gemini",
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

        if (gate && blocked) {
            System.out.println("[GATE] 차단 결정(미완성/Critical) 발견 → exit 1. "
                    + "Minor·score<80은 통과. 우회: git push --no-verify");
            System.exit(1);
        }
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
