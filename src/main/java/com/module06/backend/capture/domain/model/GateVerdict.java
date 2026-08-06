package com.module06.backend.capture.domain.model;

/*
 * 항목 하나에 대한 L3.5 판정이다.
 *
 * decisionId 는 meeting_decision.id 다. Python 쪽 itemKey 로 이 값을 문자열로 실어 보내고
 * 그대로 되돌려 받는다 — 순번 같은 임시 키를 쓰면 응답을 어느 행에 적용할지 다시 맞춰야
 * 하고, 그 맞추기가 틀리면 A 항목의 판정이 B 항목에 저장된다.
 *
 * reason 은 왜 그렇게 판정했는지다. 게이트를 조일지 풀지 나중에 판단할 근거이므로 버리지
 * 않는다. 다만 meeting_decision.reason 은 L3 의 분류 근거라 자리가 다르다 — 게이트 사유를
 * 그 컬럼에 덮으면 오분류 조사의 출발점이 사라진다. 지금은 저장하지 않고 로그로만 남긴다.
 */
public record GateVerdict(
        Long decisionId,
        GateStatus gateStatus,
        String reason
) {
}
