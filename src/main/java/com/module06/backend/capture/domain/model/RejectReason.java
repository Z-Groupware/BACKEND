package com.module06.backend.capture.domain.model;

/*
 * 왜 고쳤는지 · 왜 반려했는지다(명세 RVW-02).
 *
 * <h2>사유 코드가 곧 어느 계층이 틀렸는지를 가리킨다</h2>
 * 이게 없으면 라벨에 **"틀렸다"만 남는다.** 정확도가 나쁠 때 어디를 고쳐야 할지 알 수 없어
 * 조사를 처음부터 다시 해야 하고, 지나간 회의는 다시 만들 수 없으므로 그 조사는 불가능하다
 * (V5.9 주석). 그래서 MODIFY·REJECT 에는 사유가 필수이고 DB CHECK 로도 강제된다.
 *
 * <h2>둘은 사람이 고르지 않는다</h2>
 * WRONG_ASSIGNEE · WRONG_DUE 는 **바뀐 필드로 알 수 있다** — 담당자를 고쳤으면 담당자가 틀린
 * 것이다. 나머지 셋은 필드 변화로 알아낼 수 없어 사람이 고른다(명세: 프론트가 자동으로 채운다).
 */
public enum RejectReason {

    /* 담당자가 틀렸다. 지시어 해소가 사람을 잘못 짚은 자리다. */
    WRONG_ASSIGNEE(LayerName.L1_5),

    /* 기한이 틀렸다. tuple 추출이 상대 표현을 잘못 계산한 자리다. */
    WRONG_DUE(LayerName.L4),

    /*
     * 그런 발언 자체가 없었다.
     *
     * 명세는 L3·L4 를 함께 가리키지만 review_log.layer 는 한 값이다. 액션은 L4 의 산출물이라
     * L4 로 적는다 — 요약 항목(ANLZ-04)에서 같은 사유가 오면 그쪽이 L3 로 적는다.
     */
    HALLUCINATION(LayerName.L4),

    /* 논의였는데 확정으로 통과됐다. 확정/논의 게이트가 틀린 자리다. */
    NOT_CONFIRMED(LayerName.L3_5),

    /*
     * 이미 있는 액션과 중복이다.
     *
     * L2 를 가리킨다 — 주제 경계에 얹은 오버랩 발화가 두 주제에서 각각 항목이 되면서 생긴다.
     * 코드로 잡을 수 있는 것은 L6 이 DUPLICATE_EVIDENCE 로 먼저 잡고, 여기까지 온 것은
     * 근거 발화가 다른데 내용이 겹치는 경우다.
     */
    DUPLICATE(LayerName.L2);

    private final LayerName layer;

    RejectReason(LayerName layer) {
        this.layer = layer;
    }

    /*
     * 이 사유가 교정하는 계층. review_log.layer 에 그대로 들어간다.
     *
     * 매핑을 enum 이 갖는 이유 — 서비스에 if 로 두면 사유가 늘 때 그 분기를 빠뜨리고,
     * 빠뜨린 사유는 계층 없이 기록되거나(NOT NULL 위반) 엉뚱한 계층에 집계된다.
     */
    public LayerName layer() {
        return layer;
    }
}
