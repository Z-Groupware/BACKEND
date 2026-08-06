package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.VerifyVerdict;

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
     * L5 가 검증할 대상을 읽는다. 저장 **후에** 다시 읽는 이유는 판정을 그 행에 적어야 하기
     * 때문이다 — 메모리의 tuple 목록으로 검증하면 응답을 어느 행에 적용할지 순번으로 맞춰야
     * 하고, 그 맞추기가 틀리면 A 배정의 검증 결과가 B 배정에 저장된다(L3.5 와 같은 자리).
     *
     * 회사 스코프를 인자로 받는다. meetingId 만으로 조회하면 다른 회사 회의의 배정 내용이
     * 프롬프트에 실려 나간다 — 정확도 문제가 아니라 유출이다.
     */
    List<StoredTuple> findByMeeting(long companyId, long meetingId);

    /*
     * L5 판정을 각 tuple 행에 반영한다.
     *
     * 판정이 없는 행은 **건드리지 않는다.** verify_agree 가 NULL 로 남는 것이 "L5 가 이 행을
     * 보지 않았다"는 뜻이고, 그건 "검증에서 걸렸다"(FALSE)와 다르다. 기본값을 채우면 L5 가
     * 통째로 안 돈 회의가 전부 검토 대상으로 보인다(V5.13 주석).
     *
     * meetingId 를 함께 받는 이유는 회사 스코프다 — applyGateVerdicts 와 같다.
     *
     * @return 실제로 반영된 건수. 요청 수와 다르면 되짚지 못한 행이 있었다는 뜻이다.
     */
    int applyVerifications(long meetingId, List<TupleVerification> verifications);

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

    /*
     * 저장된 tuple 한 건. id 가 붙어 있다는 것이 TupleRow 와의 차이이고, 그 id 가 L5 판정을
     * 되짚는 키다.
     *
     * topicSeq 를 함께 주는 이유: L5 요청에 그 주제의 발화와 확정 항목을 실어야 하는데,
     * 그 재료는 L2·L3.5 산출에 있고 순번으로만 이어진다. topic 문자열로 맞추면 같은 제목의
     * 주제가 둘일 때 엉뚱한 발화가 실린다.
     */
    record StoredTuple(
            Long id,
            AssignmentTuple tuple,
            int topicSeq,
            String topic
    ) {
    }

    /*
     * tuple 하나의 L5 판정.
     *
     * agree 를 boolean(원시형)으로 둔다 — 이 레코드가 만들어졌다는 것 자체가 "L5 가 이 행을
     * 봤다"는 뜻이므로 미검증을 표현할 필요가 없다. 미검증은 레코드가 없는 것이다.
     */
    record TupleVerification(
            Long tupleId,
            boolean agree,
            List<String> disagreementFields,
            VerifyVerdict verdict,
            String reason,
            String modelName,
            String promptVersion
    ) {
    }
}
