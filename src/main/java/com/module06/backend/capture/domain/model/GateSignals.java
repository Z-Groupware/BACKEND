package com.module06.backend.capture.domain.model;

/*
 * L7 자동확정 게이트가 코드로 판정한 신호 넷이다(명세 RVW-01 `gate.signals`).
 *
 * <h2>왜 확신도 숫자가 없나</h2>
 * **모델이 스스로 말한 확신도를 쓰지 않는다.** 자기보고 신뢰도는 실제 정확도와 맞지
 * 않는다 — LLM 에 물으면 85~95 에 몰리고, 틀린 답에도 높은 숫자를 붙인다. 대신 코드로
 * 확인할 수 있는 사실만 신호로 남긴다. 화면 문구가 "AI 확신도 높음"이어도 백엔드가
 * 백분율을 만들어 내려주지는 않는다(명세 RVW-01 처리 정책).
 *
 * <h2>왜 합치지 않고 넷을 따로 두나</h2>
 * 자동확정이 틀렸을 때 **어느 조건이 헐거웠는지** 알아야 게이트를 조일 수 있다.
 * boolean 하나로 합치면 "틀렸다"만 남고 조사가 처음부터 다시 시작된다.
 *
 * @param hasEvidence       근거 발화 id 가 있다. 없으면 사람이 "정말 그런 말이 있었나"를
 *                          확인할 수 없어 검토 자체가 불가능하다
 * @param assigneeInRoster  담당자가 참석자 명단 안이다. NULL(unknown_person)이면 배정 불가다
 * @param assigneeSourceOk  명시적 호명(EXPLICIT_CALL)이거나, 1인칭(FIRST_PERSON)이면서
 *                          근거 발화의 화자가 확정됐다. 1인칭인데 화자를 모르면 "제가
 *                          할게요"의 '제가'가 누군지 모르는 것이라 근거가 성립하지 않는다
 * @param viewsAgree        L5 의 두 관점이 일치했다(verify_agree)
 */
public record GateSignals(
        boolean hasEvidence,
        boolean assigneeInRoster,
        boolean assigneeSourceOk,
        boolean viewsAgree
) {

    /*
     * 넷을 **전부** 만족하는가(명세 「자동 확정은 넷을 전부 만족할 때만」).
     *
     * 이 메서드는 L6 모순을 보지 않는다 — 신호 넷의 판정과 게이트의 최종 결론을 분리해
     * 두어야, 나중에 "신호는 다 통과했는데 모순 때문에 걸린 건"을 따로 셀 수 있다.
     * 최종 결론은 AutoConfirmGate 가 낸다.
     */
    public boolean allPassed() {
        return hasEvidence && assigneeInRoster && assigneeSourceOk && viewsAgree;
    }
}
