package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 💤 <b>휴면(dormant)</b> — 무인 자율 수정 경로. 통합 설계(review-loop/UNIFIED_DESIGN.md §3.4)에서
 * 기본 경로에서 내려왔다. 수정 주체는 드라이버(Claude Code)이고 판정은 {@link ReviewLoopRunner}가 맡는다.
 * 삭제하지 않는 이유: 검증된 테스트 자산이 붙어 있고, 무인 모드(CI 자동수정 등)를 되살릴 여지를 남긴다.
 * <p>되살릴 때의 조건: judge를 {@link ClaudeJudgeAdapter}로 교차시켜 <b>찾는 주체 ≠ 고치는 주체</b>를 지킬 것.
 *
 * <p>변경 파일들을 전역 budget 안에서 순회한다. 파일마다:
 * ReviewLoop(판정) + VerifiedFixer(수정+컴파일검증) 로 {@link AutoFixRunner}를 돌린다.
 *  - PASS로 수렴하고 코드가 바뀐 파일만 디스크에 남긴다(커밋 대상).
 *  - 미수렴(budget 소진)·Critical(AWAITING_HUMAN)·미완성(INCOMPLETE)은 원본을 복원하고 사람 인계로 표시.
 *    → 결정 C: 자율수정은 Minor(NEEDS_REVISION)만. Critical/미완성은 AutoFixRunner가 즉시 멈춘다.
 *
 * <p>판정 전용 모드(구 dryRun)는 없다 — 판정은 {@code reviewLoop --files-from ...} 하나로 일원화됐다(P2).
 * 이 클래스는 '수정'만 남는다.
 *
 * <p>경로 규약({@link ReviewLoopRunner#evidenceFor}와 동일): LLM에는 파일명만 주고, EvidenceValidator는
 * 그 파일의 부모 디렉터리를 기준으로 file:line 근거를 검증한다.
 * AutoFixRunner·VerifiedFixer의 writeRoot도 그 부모 디렉터리다.
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

    /** 파일별 수정 루프. 판정만 보고 싶으면 이 클래스가 아니라 {@code reviewLoop --files-from ...}를 쓴다. */
    public List<FileOutcome> run(List<Path> targets) throws IOException {
        List<FileOutcome> outcomes = new ArrayList<>();
        int globalSpent = 0;

        for (Path rel : targets) {
            Path abs = repoRoot.resolve(rel);
            Path parent = abs.getParent() == null ? repoRoot : abs.getParent();
            String fileName = abs.getFileName().toString();
            String original = Files.readString(abs);

            ReviewLoop loop = new ReviewLoop(judge, catalog, new EvidenceValidator(parent), scorer, lessons);

            int remaining = globalBudget - globalSpent;
            if (remaining <= 0) {
                System.out.println("[autoloop] 전역 budget 소진 → 남은 파일 스킵: " + rel);
                outcomes.add(new FileOutcome(rel.toString(), null, 0, true, false));
                continue;
            }

            ReviewBudget fileBudget = new ReviewBudget(Math.min(roundsPerFile, remaining));
            CodeFixerPort fixer = new VerifiedFixer(baseFixer, verify, parent);
            AutoFixRunner runner = new AutoFixRunner(loop, fixer, fileBudget, audit, clock, model, parent);
            AutoFixResult r;
            try {
                r = runner.run(fileName, original);
            } catch (RuntimeException | IOException e) {
                // 판정·수정 중 예외(LLM API 오류 등) — AutoFixRunner가 라운드마다 디스크에 쓰므로
                // 여기서 복원하지 않으면 중간 수정본이 작업트리에 남아 커밋에 섞인다.
                Files.writeString(abs, original);
                globalSpent += fileBudget.spent();
                System.out.printf("[autoloop] %s → 예외로 중단, 원본 복원: %s%n", rel, e);
                outcomes.add(new FileOutcome(rel.toString(), null, fileBudget.spent(), false, false));
                continue;
            }
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
