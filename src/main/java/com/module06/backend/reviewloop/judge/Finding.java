package com.module06.backend.reviewloop.judge;

/**
 * LLM Judge가 뱉는 개별 위반 항목. 점수는 여기 없다 — 점수는 JudgeScorer가 결정론으로 산출한다.
 * file/line은 Evidence 검증(환각 차단)의 근거가 된다.
 */
public record Finding(
        String ruleId,
        Severity severity,
        String category,
        String description,
        String file,
        int line,
        Confidence confidence,
        FindingSource source
) {
    /** 보조 생성자 — 출처 미지정 시 JUDGE(Gate 2)로 본다. 기존 호출부 호환용. */
    public Finding(String ruleId, Severity severity, String category, String description,
                   String file, int line, Confidence confidence) {
        this(ruleId, severity, category, description, file, line, confidence, FindingSource.JUDGE);
    }

    /** severity만 교체한 복사본 — 카탈로그 정규화(LLM이 준 severity를 카탈로그 값으로 덮어쓰기)에 쓴다. */
    public Finding withSeverity(Severity newSeverity) {
        return new Finding(ruleId, newSeverity, category, description, file, line, confidence, source);
    }
}
