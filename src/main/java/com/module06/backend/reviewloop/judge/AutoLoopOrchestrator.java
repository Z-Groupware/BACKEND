package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * P0 — 변경 파일들을 전역 budget 안에서 순회하는 자율 루프 오케스트레이터.
 *
 * 파일마다: ReviewLoop(판정) + VerifiedFixer(수정+컴파일검증) 로 {@link AutoFixRunner}를 돌린다.
 *  - PASS로 수렴하고 코드가 바뀐 파일만 디스크에 남긴다(커밋 대상).
 *  - 미수렴(budget 소진)·Critical(AWAITING_HUMAN)·미완성(INCOMPLETE)은 원본을 복원하고 사람 인계로 표시.
 *    → 결정 C: 자율수정은 Minor(NEEDS_REVISION)만. Critical/미완성은 AutoFixRunner가 즉시 멈춘다.
 *
 * 경로 규약(ReviewLoopRunner와 동일): LLM에는 파일명만 주고, EvidenceValidator는 그 파일의 부모 디렉터리를
 * 기준으로 file:line 근거를 검증한다. AutoFixRunner·VerifiedFixer의 writeRoot도 그 부모 디렉터리다.
 */
public class AutoLoopOrchestrator {

    /** 파일별 처리 결과. decision=null 은 budget 소진으로 스킵된 파일. */
    public record FileOutcome(String path, JudgeDecision decision, int rounds, boolean budgetOut, boolean changed) {}

    private final LlmJudgePort judge;
    private final RuleCatalog catalog;
    private final JudgeScorer scorer;
    private final List<Lesson> lessons;
    private final CodeFixerPort baseFixer;
    private final VerificationPort verify;
    private final AuditLogWriter audit;
    private final Clock clock;
    private final Path repoRoot;
    private final String model;
    private final int roundsPerFile;
    private final int globalBudget;

    public AutoLoopOrchestrator(LlmJudgePort judge, RuleCatalog catalog, JudgeScorer scorer, List<Lesson> lessons,
                                CodeFixerPort baseFixer, VerificationPort verify, AuditLogWriter audit,
                                Clock clock, Path repoRoot, String model, int roundsPerFile, int globalBudget) {
        this.judge = judge;
        this.catalog = catalog;
        this.scorer = scorer;
        this.lessons = lessons;
        this.baseFixer = baseFixer;
        this.verify = verify;
        this.audit = audit;
        this.clock = clock;
        this.repoRoot = repoRoot;
        this.model = model;
        this.roundsPerFile = roundsPerFile;
        this.globalBudget = globalBudget;
    }

    /** dryRun=true면 판정만(수정·디스크쓰기 없음). */
    public List<FileOutcome> run(List<Path> targets, boolean dryRun) throws IOException {
        List<FileOutcome> outcomes = new ArrayList<>();
        int globalSpent = 0;

        for (Path rel : targets) {
            Path abs = repoRoot.resolve(rel);
            Path parent = abs.getParent() == null ? repoRoot : abs.getParent();
            String fileName = abs.getFileName().toString();
            String original = Files.readString(abs);

            EvidenceValidator ev = new EvidenceValidator(parent);
            ReviewLoop loop = new ReviewLoop(judge, catalog, ev, scorer, lessons);

            if (dryRun) {
                JudgeVerdict v = loop.review(fileName, original);
                System.out.printf("[autoloop:dry] %s → score %d · %s · findings %d%n",
                        rel, v.score(), v.decision(), v.findings().size());
                outcomes.add(new FileOutcome(rel.toString(), v.decision(), 1, false, false));
                continue;
            }

            int remaining = globalBudget - globalSpent;
            if (remaining <= 0) {
                System.out.println("[autoloop] 전역 budget 소진 → 남은 파일 스킵: " + rel);
                outcomes.add(new FileOutcome(rel.toString(), null, 0, true, false));
                continue;
            }

            ReviewBudget fileBudget = new ReviewBudget(Math.min(roundsPerFile, remaining));
            CodeFixerPort fixer = new VerifiedFixer(baseFixer, verify, parent);
            AutoFixRunner runner = new AutoFixRunner(loop, fixer, fileBudget, audit, clock, model, parent);
            AutoFixResult r = runner.run(fileName, original);
            globalSpent += r.roundsUsed();

            JudgeDecision decision = r.finalVerdict() == null ? null : r.finalVerdict().decision();
            boolean changed = decision == JudgeDecision.PASS && !r.finalCode().equals(original);
            if (!changed) {
                Files.writeString(abs, original);   // 미수렴·사람인계 → 원본 복원(커밋에 안 섞이게)
            }
            System.out.printf("[autoloop] %s → %s · rounds %d%s%n",
                    rel, decision, r.roundsUsed(), r.terminatedByBudget() ? " · budget소진(사람인계)" : "");
            outcomes.add(new FileOutcome(rel.toString(), decision, r.roundsUsed(), r.terminatedByBudget(), changed));
        }
        return outcomes;
    }
}
