package com.module06.backend.reviewloop.judge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * ★ 자율 수정 루프 진입점 (P0 배선). 게이트용 {@link ReviewLoopRunner}와 분리 — 훅 순수성 유지.
 *
 *   ./gradlew reviewAutoFix --args="--files-from <list> [--domain X] [--max-files N]
 *                                   [--rounds-per-file 3] [--global-budget 6] [--dry-run]"
 *
 * 보통은 직접 부르지 않고 래퍼 scripts/review-autoloop.sh 가 별도 worktree/브랜치에서 호출한다(결정 A/B).
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
        boolean dryRun = CliArgs.flag(args, "--dry-run");

        if (filesFrom == null) {
            System.out.println("사용법: reviewAutoFix --args=\"--files-from <list> "
                    + "[--domain X] [--max-files N] [--rounds-per-file N] [--global-budget N] [--dry-run]\"");
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

        System.out.printf("== 자율 수정 루프 == 대상 %d파일 · 파일당 ≤%d · 전역 ≤%d%s%n",
                targets.size(), roundsPerFile, globalBudget, dryRun ? " · DRY-RUN" : "");

        AutoLoopOrchestrator orch = new AutoLoopOrchestrator(
                judge, catalog, scorer, lessons, baseFixer, verify, audit,
                Clock.systemUTC(), repoRoot, GeminiModels.resolve(), roundsPerFile, globalBudget);
        List<AutoLoopOrchestrator.FileOutcome> outcomes = orch.run(targets, dryRun);

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
