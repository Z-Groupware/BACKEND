package com.module06.backend.capture.domain.model;

/*
 * L5 의 VERIFY 관점이 내린 판정이다 — "이 tuple 이 근거 발화로 확인되는가".
 *
 * L5 는 두 관점으로 묻는다(EXTRACT_NARROW · VERIFY). 이 enum 은 그중 **VERIFY 한쪽의
 * 판정**이고, 두 관점을 합친 결론은 아니다. 합친 결론은 agree 이며 이것과 다를 수 있다 —
 * VERIFY 가 ACCEPT 라도 EXTRACT_NARROW 가 다른 담당자를 뽑았으면 agree 는 false 다.
 * 그래서 둘을 한 값으로 합치지 않고 따로 저장한다: agree 만 남기면 "왜 갈렸나"를 볼 수 없고,
 * verdict 만 남기면 관점 다변화를 한 의미가 사라진다.
 *
 * null 이 "VERIFY 관점이 실패했다"는 뜻이다. 그때도 EXTRACT_NARROW 가 살아 있으면 검증은
 * 수행된 것이고, 다만 안전한 쪽으로 접혀 agree=false 가 된다(Python app/layers/l5.py).
 * 그래서 이 enum 에 UNKNOWN 을 두지 않는다 — 실패는 값이 아니라 값의 부재다.
 */
public enum VerifyVerdict {

    /* 근거 발화로 확인된다. */
    ACCEPT,

    /* 확인되지 않는다. Python 은 판정값이 깨져 돌아와도 이쪽으로 내린다(애매한 것은 검토로). */
    REJECT;

    /*
     * 저장·전송된 문자열을 되돌린다. 알 수 없는 값은 **ACCEPT 로 낙관하지 않고** null 이다 —
     * 판정을 못 읽은 것과 확인됐다는 것을 같게 두면 검증이 뚫린다(GateStatus 와 같은 이유).
     */
    public static VerifyVerdict fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (VerifyVerdict verdict : values()) {
            if (verdict.name().equals(value)) {
                return verdict;
            }
        }
        return null;
    }
}
