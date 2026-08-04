package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.file.Path;

/**
 * 💤 <b>휴면(dormant)</b> — 무인 자동수정 루프. 통합 설계(review-loop/UNIFIED_DESIGN.md §3.4)에서
 * 기본 경로에서 내려왔다. 수정 라운드의 주체는 드라이버(Claude Code)이고, 절차는 review-loop/DRIVER.md다.
 * 테스트({@code AutoFixRunnerTest})가 붙은 검증 자산이라 삭제하지 않고 남긴다.
 *
 * <p>자동수정 루프 (아티팩트 §01 · Minor 한정) — 찾기→고치기→재리뷰를 budget까지 반복한다.
 *
 *   라운드마다: 코드를 디스크에 동기화 → 리뷰(Judge+Evidence+점수) → audit log
 *   - PASS           → 종료(수정 완료)
 *   - AWAITING_HUMAN → 종료(Critical은 자동수정 금지, 사람에게)
 *   - NEEDS_REVISION → Fixer가 코드를 고치고 다음 라운드 (Minor만 자동수정)
 *   - budget 소진    → 종료(무한 루프 없음), "AI가 해결 못함"으로 사람에게 이관
 *
 * 코드를 디스크에 써야 EvidenceValidator가 고친 코드의 file:line을 재검증한다.
 */
public class AutoFixRunner {

    private final RoundReviewer reviewer;
    private final CodeFixerPort fixer;
    private final ReviewBudget budget;
    private final AuditLogWriter auditLog;
    private final Clock clock;
    private final String model;
    private final Path repoRoot;

    public AutoFixRunner(RoundReviewer reviewer, CodeFixerPort fixer, ReviewBudget budget,
                         AuditLogWriter auditLog, Clock clock, String model, Path repoRoot) {
        this.reviewer = reviewer;
        this.fixer = fixer;
        this.budget = budget;
        this.auditLog = auditLog;
        this.clock = clock;
        this.model = model;
        this.repoRoot = repoRoot;
    }

    public AutoFixResult run(String filePath, String initialCode) throws IOException {
        String code = initialCode;
        JudgeVerdict verdict = null;

        while (!budget.isExhausted()) {
            budget.consume();
            Files.writeString(repoRoot.resolve(filePath), code);   // 디스크 동기화 → Evidence가 현재 코드 검증
            verdict = reviewer.review(filePath, code);

            boolean budgetOut = budget.isExhausted();
            auditLog.append(toRecord(verdict, !isTerminal(verdict) && budgetOut));

            if (isTerminal(verdict)) {
                return new AutoFixResult(code, verdict, budget.spent(), false);
            }
            if (budgetOut) {
                return new AutoFixResult(code, verdict, budget.spent(), true);
            }
            // NEEDS_REVISION (Minor) → 자동수정 후 다음 라운드
            code = fixer.fix(filePath, code, verdict.findings());
        }
        return new AutoFixResult(code, verdict, budget.spent(), true);
    }

    /**
     * 루프를 멈추는 결정 — 자동수정을 시도하지 않고 그 자리에서 끝낸다.
     *
     * <p>{@code INCOMPLETE}가 여기 포함돼야 한다. 빠져 있으면 아래로 흘러 {@code fixer.fix()}가 불리는데,
     * 그건 "미완성은 사람 인계"라는 정책(클래스 javadoc·AutoLoopOrchestrator·UNIFIED_DESIGN 결정 C)과
     * 정면으로 어긋난다. <b>문서는 금지한다고 하는데 코드는 하고 있던 상태였다.</b>
     *
     * <p>CRITICAL은 별도 가드가 필요 없다 — {@link JudgeScorer}가 {@code hasCritical}을 점수 분기보다
     * 먼저 보고 {@code AWAITING_HUMAN}으로 라우팅하므로, {@code NEEDS_REVISION} 판정에는 CRITICAL이 섞일 수 없다.
     */
    private boolean isTerminal(JudgeVerdict v) {
        return v.decision() == JudgeDecision.PASS
                || v.decision() == JudgeDecision.AWAITING_HUMAN
                || v.decision() == JudgeDecision.INCOMPLETE;
    }

    private AuditRecord toRecord(JudgeVerdict v, boolean terminatedByBudget) {
        return new AuditRecord(LocalDateTime.now(clock).toString(), budget.spent(), model,
                v.score(), v.hasCritical(), v.decision(), v.findings().size(), terminatedByBudget);
    }
}
