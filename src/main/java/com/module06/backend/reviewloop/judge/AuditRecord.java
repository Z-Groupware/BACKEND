package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 감사 로그 한 줄(error_log.jsonl) — "언제/어떤 모델로/몇 점/무슨 판정"을 재현 가능하게 남긴다. (아티팩트 §01 ③)
 * 결제 도메인이면 이건 선택이 아니라 컴플라이언스에 가까운 추적성이다.
 *
 * <p>{@code skipReason}이 채워진 행은 <b>판정이 수행되지 않은 행</b>이다({@link GateSkipRecorder}).
 * 판정 라운드와 같은 파일에 남기는 이유: "리뷰가 몇 번 돌았나"와 "몇 번 건너뛰었나"를 따로 두면
 * 둘을 대조할 때 파일 두 개를 시각으로 맞춰야 하고, 그러면 아무도 대조하지 않는다.
 * 이 행에서 {@code findingsCount}는 findings 수가 아니라 <b>리뷰되지 않은 .java 수</b>다
 * ({@link #unreviewedFiles()} 로 읽을 것) — 생략된 실행에는 finding 자체가 존재하지 않는다.
 */
public record AuditRecord(
        String timestamp,
        int round,
        String model,
        int score,
        boolean hasCritical,
        JudgeDecision decision,
        int findingsCount,
        boolean terminatedByBudget,
        String skipReason
) {

    /** 판정이 실제로 돌아간 라운드 — 기존 호출부(형제 러너 3곳)를 그대로 둔다. */
    public AuditRecord(String timestamp, int round, String model, int score, boolean hasCritical,
                       JudgeDecision decision, int findingsCount, boolean terminatedByBudget) {
        this(timestamp, round, model, score, hasCritical, decision, findingsCount, terminatedByBudget, null);
    }

    /**
     * 판정을 생략한 실행.
     *
     * <p>{@code round=0}·{@code model=null}·{@code decision=null}이다 — 라운드도 모델도 판정도
     * 존재하지 않았다. 0점·PASS 같은 그럴듯한 기본값을 넣으면 집계에서 통과한 판정과 섞인다.
     */
    static AuditRecord skipped(String timestamp, String reason, int unreviewedFiles) {
        return new AuditRecord(timestamp, 0, null, 0, false, null, unreviewedFiles, false, reason);
    }

    /**
     * 이 행이 '판정 미수행' 기록인가.
     *
     * <p>{@code @JsonIgnore} 가 필수다 — 없으면 Jackson 이 {@code is} 접두사를 getter 로 보고
     * {@code "skipped"} 속성을 하나 더 써넣고, 그 줄을 다시 읽을 때 미지 속성으로 거부된다.
     * {@link ReviewReport#fromFiles}는 그런 줄을 '손상된 줄'로 조용히 건너뛰므로,
     * 기록은 남았는데 리포트에서만 사라지는 상태가 된다.
     */
    @JsonIgnore
    public boolean isSkipped() {
        return skipReason != null;
    }

    /** 생략 행에서 리뷰되지 않은 .java 수. 판정 행에서는 의미가 없다(0). */
    @JsonIgnore
    public int unreviewedFiles() {
        return isSkipped() ? findingsCount : 0;
    }
}
