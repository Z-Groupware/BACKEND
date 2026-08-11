package com.module06.backend.meeting.application.port.out;

import java.util.List;

/*
 * D 회의 도메인이 A 분석 도메인에 회의별 요약 중단·실패 상태를 묻는 출력 Port다.
 *
 * A의 엔티티와 상태 판정 구현을 D에 노출하지 않고 마이페이지 카드에 필요한 최소 값만 받는다.
 */
public interface SummaryStatusQueryPort {

    /* 후보 회의 중 요약이 중단되거나 실패한 회의만 배치로 조회한다. */
    List<StalledSummaryMeeting> findStalledSummaries(Long companyId, List<Long> meetingIds);

    /* 요약 문제 회의의 식별자와 화면 문구를 가르는 중단 여부를 담는 D 소유 읽기 모델이다. */
    record StalledSummaryMeeting(Long meetingId, boolean stalled) {
    }
}
