package com.module06.backend.capture.application.port.out;

import java.util.List;
import java.util.Optional;

import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.TopicItem;

/*
 * meeting_summary(V5.7) · meeting_decision(V5.8) 접근 포트다.
 *
 * ⚠ 이 두 테이블은 소유권이 미결이다(V5.7 주석). A(이태연)와 D(모성진) 중 누가 쓰기를
 * 갖는지 정해지지 않았다. 포트로 감싸 두면 소유가 D 로 넘어가도 오케스트레이터는 안 바뀐다.
 */
public interface MeetingSummaryRepository {

    /*
     * 회의 요약을 통째로 갈아끼운다(요약 1건 + 주제별 항목 N건).
     *
     * 갱신이 아니라 교체인 이유: ANLZ-01 강제 재실행은 처음부터 다시 도는 것이므로 이전
     * 산출물이 남아 있으면 안 된다. 남기면 두 번 돌린 회의에 항목이 두 배로 쌓이고,
     * 사용자는 같은 결정을 두 번 보게 된다. UNIQUE(meeting_id) 가 요약 쪽을 강제한다.
     *
     * 사람이 손댄 요약(edited_at NOT NULL)을 덮는 문제는 이 슬라이스 범위 밖이다 —
     * ANLZ-04(수정)가 붙을 때 함께 정한다.
     */
    void replace(long companyId, long meetingId, String overview, List<TopicDecisions> topics,
                 String modelName, String promptVersion);

    /*
     * L3.5 판정을 meeting_decision.gate_status 에 반영한다.
     *
     * 판정이 없는 항목은 **건드리지 않는다.** NULL 로 남는 것이 "게이트가 판정하지 못했다"는
     * 뜻이고, 그 항목은 L4 로 넘어가지 않는다. 여기서 기본값을 채우면 미판정과 판정이 한 값으로
     * 뭉쳐서, 게이트를 부르지 못한 회의와 게이트가 논의로 판정한 회의를 구분할 수 없게 된다.
     *
     * meetingId 를 함께 받는 이유는 회사 스코프다. verdict 의 decisionId 는 계층 응답에서
     * 온 값이므로, 그것만으로 갱신하면 다른 회의(다른 회사)의 항목을 고칠 수 있는 경로가 생긴다.
     *
     * @return 실제로 반영된 건수. 요청 판정 수와 다르면 되짚지 못한 항목이 있었다는 뜻이다.
     */
    int applyGateVerdicts(long meetingId, List<GateVerdict> verdicts);

    /* ANLZ-03 조회. 분석 전이면 비어 있다 — 빈 요약을 만들어 돌려주지 않는다. */
    /*
     * 회사 스코프를 **인자로 받는다.** meetingId 만으로 조회하면 다른 회사 회의의 요약과
     * 근거 발화 id 가 나간다 — 메타데이터가 아니라 회의 내용이다(CWE-639).
     */
    Optional<MeetingSummaryView> findByMeeting(long companyId, long meetingId);

    /* 한 주제와 그 안의 항목들. topicSeq 는 L2 가 매긴 순번을 그대로 쓴다. */
    record TopicDecisions(int topicSeq, String topic, List<TopicItem> items) {
    }

    /* ANLZ-03 응답의 재료. */
    record MeetingSummaryView(String overview, List<TopicView> topics) {
    }

    record TopicView(int topicSeq, String topic, List<ItemView> items) {
    }

    record ItemView(Long id, ItemType itemType, String content, String reason,
                    Long evidenceUtteranceId, String gateStatus) {
    }
}
