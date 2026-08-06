package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.AssignmentTuple;

/*
 * meeting_assignment_tuple(V5.12) 접근 포트다.
 *
 * 이 테이블은 tuple 의 **대기실**이고 최종 도착지가 아니다. L5(검증) · L6(모순 검사) ·
 * L7(자동확정 게이트)을 지난 행만 action 으로 분배된다 — action 은 C 도메인 소유이고
 * 상태 전이 메서드가 미정이므로, 그 경로가 정해질 때 이 포트에 분배 메서드가 붙는다.
 * 지금 action 에 직접 쓰면 나중에 되돌릴 코드가 된다(V5.12 주석).
 */
public interface AssignmentTupleRepository {

    /*
     * 회의의 tuple 을 통째로 갈아끼운다.
     *
     * 갱신이 아니라 교체인 이유는 meeting_summary 와 같다 — ANLZ-01 강제 재실행은 처음부터
     * 다시 도는 것이므로 이전 산출물이 남아 있으면 같은 배정이 두 배로 쌓인다.
     *
     * ⚠ 실제로는 meeting_decision 교체가 ON DELETE CASCADE 로 이 테이블을 먼저 비운다
     * (V5.12 주석). 그래도 여기서 다시 지우는 이유는 L3 는 성공했는데 L4 만 재실행하는
     * 경로가 ANLZ-02 로 붙을 예정이기 때문이다 — 그때 CASCADE 는 돌지 않는다.
     */
    void replace(long companyId, long meetingId, List<TupleRow> rows);

    /*
     * 저장할 tuple 한 건. 계층 산출(AssignmentTuple)에 "어디서 나왔는지"를 붙인 것이다.
     *
     * decisionId 는 이 tuple 을 뽑은 CONFIRMED 항목이다. 없으면 어느 결정에서 나온 배정인지
     * 모르게 되고, 사람이 검토할 때 근거 항목으로 되짚을 수 없다.
     */
    record TupleRow(
            AssignmentTuple tuple,
            Long decisionId,
            int topicSeq,
            String topic,
            String modelName,
            String promptVersion
    ) {
    }
}
