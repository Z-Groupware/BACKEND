package com.module06.backend.meeting.application.port.out;

import java.util.List;
import java.util.Optional;

import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;
import com.module06.backend.meeting.domain.model.MeetingTranscriptStatus;

/*
 * D 회의 도메인이 A 분석 도메인에 회의별 요약 중단·실패 상태를 묻는 출력 Port다.
 *
 * A의 엔티티와 상태 판정 구현을 D에 노출하지 않고 마이페이지 카드에 필요한 최소 값만 받는다.
 */
public interface SummaryStatusQueryPort {

    /* 후보 회의 중 요약이 중단되거나 실패한 회의만 배치로 조회한다. */
    List<StalledSummaryMeeting> findStalledSummaries(Long companyId, List<Long> meetingIds);

    /* 회의 상세가 사용할 전체 요약 상태를 조회한다. 기존 테스트 대역 호환을 위해 기본값을 둔다. */
    default List<SummaryStatusMeeting> findSummaryStatuses(Long companyId, List<Long> meetingIds) {
        return List.of();
    }

    /* 회의 상세의 발화 기록 영역이 사용할 STT 정본 상태를 조회한다. */
    default Optional<TranscriptStatusMeeting> findTranscriptStatus(Long companyId, Long meetingId) {
        return Optional.empty();
    }

    /* 요약 문제 회의의 식별자와 화면 문구를 가르는 중단 여부를 담는 D 소유 읽기 모델이다. */
    record StalledSummaryMeeting(Long meetingId, boolean stalled) {
    }

    /* A가 계산한 전체 요약 상태를 D 소유 enum으로 복사한 읽기 모델이다. */
    record SummaryStatusMeeting(Long meetingId, MeetingSummaryStatus status) {
    }

    /* A가 계산한 STT 정본 상태를 D 소유 enum으로 복사한 읽기 모델이다. */
    record TranscriptStatusMeeting(Long meetingId, MeetingTranscriptStatus status) {
    }
}
