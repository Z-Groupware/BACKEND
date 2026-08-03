package com.module06.backend.reviewloop.judge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 💤 <b>휴면(dormant)</b> — 무인 자율 수정 루프 진입점. 통합 설계(review-loop/UNIFIED_DESIGN.md §3.4)에서
 * 기본 경로·문서에서 내려왔다. <b>기본 절차는 review-loop/DRIVER.md의 드라이버 루프</b>다
 * (판정=Gemini · 수정=Claude Code · 검증=scripts/review-verify.sh).
 *
 * <p>여기 남은 이유는 무인 모드(CI 자동수정 등) 재개 여지다. 되살릴 때는 judge를
 * {@link ClaudeJudgeAdapter}로 교차시켜 <b>찾는 주체 ≠ 고치는 주체</b> 불변식을 지킬 것 —
 * 현재 배선은 judge·fixer가 모두 Gemini라 자기 승인이다(그래서 휴면).
 *
 * <pre>
 *   ./gradlew reviewAutoFix --args="--files-from &lt;list&gt; [--domain X] [--max-files N]
 *                                   [--rounds-per-file 3] [--global-budget 6]"
 * </pre>
 *
 * 판정만 필요하면 이걸 쓰지 않는다 — {@code ./gradlew reviewLoop --args="--files-from <list>"}가
 * 유일한 판정 경로다(P2 일원화. 구 {@code --dry-run} 제거).
 * 격리가 필요하면 scripts/review-session.sh 로 worktree를 먼저 만든다.
 * GEMINI_API_KEY 없으면 판정·수정이 불가하므로 조용히 생략한다.
 */
public final class AutoLoopRunner {

    public static void main(String[] args) throws Exception {
        String filesFrom = CliArgs.value(args, "--files-from", null);
        String domain = CliArgs.value(args, "--domain", null);
        String rulesPath = CliArgs.value(args, "--rules", "review-loop/rules.yaml");
        int maxFiles = Integer.parseInt(CliArgs.value(args, "--max-files", "5"));
        int roundsPerFile = Integer.parseInt(CliArgs.value(args, "--rounds-per-file", "3"));
        int globalBudget = Integer.parseInt(CliArgs.value(args, "--global-budget", "6"));

        if (filesFrom == null) {
            System.out.println("사용법: reviewAutoFix --args=\"--files-from <list> "
                    + "[--domain X] [--max-files N] [--rounds-per-file N] [--global-budget N]\"");
            System.out.println("  ⚠️ 휴면 경로(judge·fixer 모두 Gemini = 자기승인). 기본 절차는 review-loop/DRIVER.md");
            System.out.println("  판정만 필요하면: ./gradlew reviewLoop --args=\"--files-from <list>\"");
            return;
        }

        List<Path> targets = loadTargets(filesFrom, maxFiles);
        if (targets.isEmpty()) {
            System.out.println("[autoloop] 자율수정 대상 .java 없음 → 종료");
            return;
        }

        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("[autoloop] GEMINI_API_KEY 없음 → 자율 루프 생략(판정·수정 불가)");
            return;
        }

        Path repoRoot = Path.of("").toAbsolutePath();
        RuleCatalog catalog = RuleCatalog.fromFile(Path.of(rulesPath));
        if (domain != null) {
            catalog = catalog.forDomain(domain);
        }
        LlmJudgePort judge = new GeminiJudgeAdapter();
        // 가중치·임계값은 rules.yaml(meta.score)이 SSOT — ReviewLoopRunner와 동일 경로를 쓴다.
        JudgeScorer scorer = new JudgeScorer(
                catalog.effectiveWeights(),
                catalog.scorePolicy().defaultWeightBySeverity(),
                catalog.scorePolicy().passThreshold());
        List<Lesson> lessons = new KnowledgeStore(ReviewLoopPaths.LESSONS).lessons();
        Files.createDirectories(ReviewLoopPaths.AUDIT_LOG.getParent());
        AuditLogWriter audit = new AuditLogWriter(ReviewLoopPaths.AUDIT_LOG);

        CodeFixerPort baseFixer = new GeminiCodeFixerAdapter();
        VerificationPort verify = new CompileVerification();

        System.out.println("== 자율 수정 루프(휴면 경로) == judge·fixer가 같은 모델이다 — 기본 절차는 review-loop/DRIVER.md");
        System.out.printf("대상 %d파일 · 파일당 ≤%d · 전역 ≤%d%n", targets.size(), roundsPerFile, globalBudget);

        AutoLoopOrchestrator orch = new AutoLoopOrchestrator(
                judge, catalog, scorer, lessons, baseFixer, verify, audit,
                Clock.systemUTC(), repoRoot, GeminiModels.resolve(), roundsPerFile, globalBudget);
        List<AutoLoopOrchestrator.FileOutcome> outcomes = orch.run(targets);

        long changed = outcomes.stream().filter(AutoLoopOrchestrator.FileOutcome::changed).count();
        long humans = outcomes.stream()
                .filter(o -> o.budgetOut()
                        || o.decision() == JudgeDecision.AWAITING_HUMAN
                        || o.decision() == JudgeDecision.INCOMPLETE)
                .count();
        System.out.printf("%n== 요약 == 수렴·수정 %d · 사람인계 %d · 전체 %d%n",
                changed, humans, outcomes.size());
    }

    /** files-from 목록 → 실재하는 .java Path (repo-relative). max-files로 비용 방어. */
    private static List<Path> loadTargets(String filesFrom, int maxFiles) throws Exception {
        Path list = Path.of(filesFrom);
        if (!Files.exists(list)) {
            System.out.println("[autoloop] 변경파일 목록 없음: " + filesFrom);
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        for (String raw : Files.readAllLines(list)) {
            String s = raw.replace("﻿", "").trim();   // BOM/공백 방어
            if (s.isBlank() || !s.endsWith(".java")) {
                continue;
            }
            Path p = Path.of(s);
            if (Files.exists(p)) {
                files.add(p);
            }
        }
        if (files.size() > maxFiles) {
            System.out.printf("[autoloop] 변경 .java %d개 중 %d개만 처리(--max-files) · 나머지 %d개 스킵%n",
                    files.size(), maxFiles, files.size() - maxFiles);
            return List.copyOf(files.subList(0, maxFiles));
        }
        return files;
    }

    private AutoLoopRunner() {
    }
}
