package com.module06.backend.meeting.application.port.out;

import java.util.List;

/*
 * D 회의 도메인이 A 분석 도메인에 회의별 확정 결정 요약을 요청하는 아웃바운드 Port다.
 *
 * A의 엔티티나 저장소를 직접 참조하지 않고 D가 필요한 최소 읽기 모델만 계약으로 노출한다.
 */
public interface DecisionSummaryQueryPort {

    /* 회사 범위의 회의에서 출처 회의 카드에 공개할 확정 결정 목록을 조회한다. */
    List<DecisionSummary> findDecisionSummaries(Long companyId, Long meetingId);

    /* 결정 식별자와 표시 본문·근거를 담는 D 소유 읽기 모델이다. */
    record DecisionSummary(Long decisionId, String content, String reason) {
    }
}
