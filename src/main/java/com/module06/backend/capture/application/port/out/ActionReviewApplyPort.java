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
 * <h2>여섯 값만 바꾼다</h2>
 * 검토 화면이 고치는 것은 담당자 · 기한 · 제목 · 내용 · 예정 시작일 · 검토 상태뿐이다
 * (명세 RVW-02 의 value).
 * 2026-08-11 — 제목·내용 인라인 수정 지원(이홍근 요청)으로 title·detail 두 파라미터를 추가한다.
 * 2026-08-12 — 예정 시작일(plannedStartDate) 추가(#386 후속 · 이홍근 요청).
 * "AI 가 쓴 문장"과 "사람이 고친 문장"의 경계는 여전히 review_log 라벨링이 지킨다 —
 * ApplyReviewDecisionService가 human_value/llm_output을 분리해서 남긴다.
 *
 * <h2>⚠ plannedStartDate 는 AI 산출물이 아니다</h2>
 * 다른 다섯과 성질이 다르다. meeting_assignment_tuple 에 대응 컬럼이 없어 **AI 가 애초에 내지
 * 않는 값**이고, 사람이 검토 화면에서 처음 정한다. 그래서 이 값의 변경은 review_log 에
 * WRONG_* 라벨을 만들지 않는다 — 모델이 말한 적도 없는 것을 틀렸다고 가르치면 안 된다
 * (ApplyReviewDecisionService#appendReviewLogs). human_value 에도 담지 않는다(llm_output 에
 * 대응 값이 없어 쌍이 성립하지 않는다).
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
     * @param plannedStartDate 새 예정 시작일. null 이면 바꾸지 않는다(2026-08-12 추가).
     *                         <p>⚠ **범위검증(익일 ~ 프로젝트 마감일)은 C 가 한다.** 여기서
     *                         프로젝트 마감일을 함께 넘기지 않는 이유가 그것이다 — 그 값은
     *                         project 소유이고 A 는 갖고 있지 않다(ReviewTarget 에 없다).
     *                         A 가 굳이 조회해 넘기면 **검증의 기준선을 A 가 정하게** 되고,
     *                         그 값이 낡았을 때 잘못된 상한으로 통과·거절이 갈린다.
     *                         C 는 action.projectId 로 자기 데이터에서 마감일을 꺼내
     *                         {@code Action#applyHumanReview} 에 함께 넘긴다
     * @param reviewStatus     HUMAN_CONFIRMED 또는 REJECTED. 이름을 문자열로 넘기는 이유는
     *                         C 의 enum(ActionReviewStatus)을 A 가 import 하면 그 지점에서
     *                         도메인 경계가 사라지기 때문이다
     */
    /*
     * ⚠ LocalDate 파라미터가 둘이다(dueDate · plannedStartDate). 위치가 바뀌어도 컴파일되므로
     * 호출자는 순서를 반드시 확인할 것 — 바뀌면 기한과 예정 시작일이 서로 뒤집혀 저장되고,
     * 둘 다 유효한 날짜라 아무 예외도 나지 않는다. 파라미터가 더 늘면 record 로 묶어야 한다.
     */
    void apply(long companyId, long actionId, Long assigneeMemberId, LocalDate dueDate,
               String title, String detail, LocalDate plannedStartDate, String reviewStatus);
}
