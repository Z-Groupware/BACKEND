package com.module06.backend.capture.domain.model;

/*
 * 왜 고쳤는지 · 왜 반려했는지다(명세 RVW-02).
 *
 * <h2>사유 코드가 곧 어느 계층이 틀렸는지를 가리킨다</h2>
 * 이게 없으면 라벨에 **"틀렸다"만 남는다.** 정확도가 나쁠 때 어디를 고쳐야 할지 알 수 없어
 * 조사를 처음부터 다시 해야 하고, 지나간 회의는 다시 만들 수 없으므로 그 조사는 불가능하다
 * (V5.9 주석).
 *
 * <h2>두 용도로 나뉜다(2026-08-11, 이홍근 확인)</h2>
 * **수정 사유** — WRONG_ASSIGNEE·WRONG_DUE·WRONG_TITLE·WRONG_DETAIL은 사람이 고르지 않는다.
 * **바뀐 필드로 알 수 있다** — 담당자를 고쳤으면 담당자가 틀린 것이다(명세: 프론트가 자동으로
 * 채운다). 담당자·기한·제목·내용을 한 번에 여러 개 고치면 ApplyReviewDecisionService가
 * 고친 필드 개수만큼 review_log를 나눠 기록한다 — 이 enum 자체는 필드 하나에 사유 하나라는
 * 원칙을 그대로 유지한다.
 * **반려 사유** — HALLUCINATION·DUPLICATE·NOT_ACTION·NOT_ATTENDANCE·ETC는 액션 자체를 통째로
 * 버릴 때 사람이 드롭다운에서 직접 고른다. 2026-08-11 PM 확정으로 기존 3종(HALLUCINATION·
 * NOT_CONFIRMED·DUPLICATE)에서 NOT_CONFIRMED를 NOT_ACTION으로 대체하고 NOT_ATTENDANCE·ETC를
 * 새로 추가해 5종이 됐다. ETC는 사용자가 텍스트를 입력하지 않는다 — 버튼 하나로 고르는
 * 순수 enum 값이다.
 */
public enum RejectReason {

    /* 담당자가 틀렸다. 지시어 해소가 사람을 잘못 짚은 자리다. MODIFY에서 BE가 자동으로 붙인다. */
    WRONG_ASSIGNEE(LayerName.L1_5, false),

    /* 기한이 틀렸다. tuple 추출이 상대 표현을 잘못 계산한 자리다. MODIFY에서 BE가 자동으로 붙인다. */
    WRONG_DUE(LayerName.L4, false),

    /* 제목이 틀렸다(2026-08-11 추가). MODIFY에서 BE가 자동으로 붙인다. */
    WRONG_TITLE(LayerName.L4, false),

    /* 내용(상세 설명)이 틀렸다(2026-08-11 추가). MODIFY에서 BE가 자동으로 붙인다. */
    WRONG_DETAIL(LayerName.L4, false),

    /*
     * 그런 발언 자체가 없었다. 사람이 반려 사유로 직접 고른다.
     *
     * 명세는 L3·L4 를 함께 가리키지만 review_log.layer 는 한 값이다. 액션은 L4 의 산출물이라
     * L4 로 적는다 — 요약 항목(ANLZ-04)에서 같은 사유가 오면 그쪽이 L3 로 적는다.
     */
    HALLUCINATION(LayerName.L4, true),

    /*
     * 이미 있는 액션과 중복이다. 사람이 반려 사유로 직접 고른다.
     *
     * L2 를 가리킨다 — 주제 경계에 얹은 오버랩 발화가 두 주제에서 각각 항목이 되면서 생긴다.
     * 코드로 잡을 수 있는 것은 L6 이 DUPLICATE_EVIDENCE 로 먼저 잡고, 여기까지 온 것은
     * 근거 발화가 다른데 내용이 겹치는 경우다.
     */
    DUPLICATE(LayerName.L2, true),

    /*
     * 액션으로 분배할 내용이 아니다(2026-08-11, 구 NOT_CONFIRMED 대체). 사람이 직접 고른다.
     *
     * 논의였을 뿐 확정된 할 일이 아니었거나, 그 밖에 "이건 애초에 액션이 아니다"라고 판단되는
     * 경우를 폭넓게 아우른다. 확정/논의 게이트(L3.5)가 틀린 자리로 본다.
     */
    NOT_ACTION(LayerName.L3_5, true),

    /*
     * 담당자가 이 회의에 참석하지 않았다(2026-08-11 추가). 사람이 직접 고른다.
     *
     * 담당자 지정 자체가 틀린 경우라 WRONG_ASSIGNEE와 같은 계층(화자 귀속·지시어 해소)이 잘못
     * 짚은 자리로 본다.
     */
    NOT_ATTENDANCE(LayerName.L1_5, true),

    /*
     * 기타 사유(2026-08-11 추가). 사람이 텍스트를 입력하지 않고 버튼만 눌러 직접 고른다.
     *
     * 특정 계층을 지목할 근거가 없어, CONFIRM(사유 없음)과 같은 기존 관례를 따라 액션을 만든
     * 계층(L4)으로 적는다.
     */
    ETC(LayerName.L4, true);

    private final LayerName layer;
    private final boolean humanSelectable;

    RejectReason(LayerName layer, boolean humanSelectable) {
        this.layer = layer;
        this.humanSelectable = humanSelectable;
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

    /*
     * REJECT 화면 드롭다운에 사람이 직접 고르는 값이면 true(2026-08-11 추가).
     * WRONG_* 넷은 MODIFY에서 바뀐 필드로 BE가 자동으로 붙이는 값이라 false다 —
     * REJECT 요청에 WRONG_* 가 오면 이 플래그로 거절한다(ApplyReviewDecisionService).
     */
    public boolean isHumanSelectable() {
        return humanSelectable;
    }
}
