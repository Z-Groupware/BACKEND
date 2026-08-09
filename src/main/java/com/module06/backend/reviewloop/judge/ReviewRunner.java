package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 반복 루프 실행기 — PASS/AWAITING_HUMAN/budget소진까지 라운드를 돌리고, 각 라운드를 감사 로그에 남긴다. (아티팩트 §01)
 *
 *   라운드마다: budget 소비 → 리뷰 → audit log 기록
 *   - PASS / AWAITING_HUMAN → 즉시 종료
 *   - NEEDS_REVISION → 다음 라운드 (실제 코드 수정은 LLM 자동수정 단계가 라운드 사이에 담당 — 다음 스텝)
 *   - budget 소진 → 종료(무한 루프 없음), 마지막 기록에 terminatedByBudget=true
 *
 * 시각은 팀 컨벤션(ClockConfig)에 맞춰 주입된 Clock을 쓴다 — CONV_001(재발명 금지)을 스스로도 지킨다.
 */
public class ReviewRunner {

    private final RoundReviewer reviewer;
    private final ReviewBudget budget;
    private final AuditLogWriter auditLog;
    private final Clock clock;
    private final String model;

    public ReviewRunner(RoundReviewer reviewer, ReviewBudget budget,
                        AuditLogWriter auditLog, Clock clock, String model) {
        this.reviewer = reviewer;
        this.budget = budget;
        this.auditLog = auditLog;
        this.clock = clock;
        this.model = model;
    }

    public AuditSummary run(String filePath, String code) throws IOException {
        JudgeVerdict verdict = null;

        while (!budget.isExhausted()) {
            budget.consume();
            verdict = reviewer.review(filePath, code);

            // PASS/AWAITING_HUMAN만 종료. NEEDS_REVISION·INCOMPLETE는 다음 라운드로 반복.
            boolean terminal = verdict.decision() == JudgeDecision.PASS
                    || verdict.decision() == JudgeDecision.AWAITING_HUMAN;
            boolean budgetOut = budget.isExhausted();
            boolean terminatedByBudget = !terminal && budgetOut;

            auditLog.append(toRecord(verdict, terminatedByBudget));

            if (terminal) {
                return new AuditSummary(verdict, budget.spent(), false);
            }
            if (budgetOut) {
                return new AuditSummary(verdict, budget.spent(), true);
            }
            // NEEDS_REVISION & budget 남음 → 다음 라운드
        }
        return new AuditSummary(verdict, budget.spent(), true);
    }

    private AuditRecord toRecord(JudgeVerdict v, boolean terminatedByBudget) {
        return new AuditRecord(
                LocalDateTime.now(clock).toString(),
                budget.spent(),
                model,
                v.score(),
                v.hasCritical(),
                v.decision(),
                v.findings().size(),
                terminatedByBudget);
    }
}
