package com.module06.backend.capture.domain.model;

/*
 * L3.5 확정/논의 게이트의 판정이다. meeting_decision.gate_status 에 그대로 저장된다.
 *
 * **CONFIRMED 만 L4 로 넘어간다.** 그래서 이 enum 은 정확도 지표가 아니라 관문이다 —
 * 잘못 올린 항목은 아직 합의도 안 된 일이 담당자에게 배정되는 결과가 된다.
 *
 * DISCUSSED 를 버리지 않고 남기는 이유: "이 안건은 담당자가 안 정해졌다"고 짚어주는 것 자체가
 * 결과물이고, 나중에 게이트를 조일지 풀지 판단할 근거가 된다(V5.8 주석).
 *
 * ⚠ 게이트가 아직 안 돈 것(NULL)과 DISCUSSED 는 다르다. 그래서 이 enum 에 UNKNOWN 을 두지
 * 않는다 — 두면 두 상태가 한 값으로 뭉쳐서, 게이트를 부르지 못한 회의와 게이트가 논의로
 * 판정한 회의를 구분할 수 없게 된다. 미판정은 컬럼 NULL 로만 표현한다.
 */
public enum GateStatus {

    /* 확정. L4 로 넘어간다. */
    CONFIRMED,

    /* 논의에 머물렀다. 남기지만 L4 로 넘기지 않는다. */
    DISCUSSED;

    /*
     * 저장된 문자열을 되돌린다. 알 수 없는 값은 **CONFIRMED 로 낙관하지 않고** null 을 준다 —
     * 판정을 못 읽었다는 것과 확정이라는 것을 같게 두면 게이트가 뚫린다.
     */
    public static GateStatus fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (GateStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
