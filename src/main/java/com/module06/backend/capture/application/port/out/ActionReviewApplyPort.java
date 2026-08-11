package com.module06.backend.capture.application.port.out;

import java.time.LocalDate;

/*
 * 사람의 판정을 action 에 반영한다. **A 가 선언하고 C(과제)가 구현하는 아웃바운드 포트다** —
 * 인수인계(E)가 ActionReassignPort 를 선언하고 C 가 어댑터로 배선한 것과 같은 방향이다.
 *
 * <h2>왜 A 가 직접 UPDATE 하지 않는가</h2>
 * action 은 C 소유다. RVW-01 이 그 테이블을 JdbcTemplate 으로 **읽기만** 하는 이유가
 * "이쪽이 그 테이블에 쓰기를 할 수 있게 되는 것"을 막기 위해서였는데(ActionReviewQueryPort
 * 주석), 여기서 UPDATE 문을 쓰면 그 판단을 뒤집는 것이 된다. 상태 전이 규칙(담당자 없는
 * PERSONAL 을 막는 것 · 확정 시각을 찍는 것)은 C 의 도메인 모델이 갖고 있어야 한다.
 *
 * <h2>다섯 값만 바꾼다</h2>
 * 검토 화면이 고치는 것은 담당자 · 기한 · 제목 · 내용 · 검토 상태뿐이다(명세 RVW-02 의 value).
 * 2026-08-11 — 제목·내용 인라인 수정 지원(이홍근 요청)으로 title·detail 두 파라미터를 추가한다.
 * "AI 가 쓴 문장"과 "사람이 고친 문장"의 경계는 여전히 review_log 라벨링이 지킨다 —
 * ApplyReviewDecisionService가 human_value/llm_output을 분리해서 남긴다.
 */
public interface ActionReviewApplyPort {

    /*
     * 판정을 반영한다.
     *
     * @param assigneeMemberId 새 담당자. **null 이면 담당자를 바꾸지 않는다** — 비우라는 뜻이
     *                         아니다. 담당자 지우기는 검토 화면에 없는 동작이고, PERSONAL
     *                         액션은 담당자가 필수다
     * @param dueDate          새 기한. null 이면 바꾸지 않는다. 사람이 기한을 고쳤다면
     *                         due_date_defaulted 는 false 가 되어야 한다 —
     *                         프로젝트 마감일로 채운 값이 아니게 되므로
     * @param title            새 제목. null 이면 바꾸지 않는다(2026-08-11 추가).
     * @param detail           새 내용. null 이면 바꾸지 않는다(2026-08-11 추가). C 쪽 필드명은
     *                         description이지만, 이 화면의 기존 응답(ActionReviewResponse)이
     *                         이미 detail로 부르고 있어 이름을 맞춘다.
     * @param reviewStatus     HUMAN_CONFIRMED 또는 REJECTED. 이름을 문자열로 넘기는 이유는
     *                         C 의 enum(ActionReviewStatus)을 A 가 import 하면 그 지점에서
     *                         도메인 경계가 사라지기 때문이다
     */
    void apply(long companyId, long actionId, Long assigneeMemberId, LocalDate dueDate,
               String title, String detail, String reviewStatus);
}
