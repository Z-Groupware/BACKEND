package com.module06.backend.meeting.infrastructure.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;
import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;
import com.module06.backend.meeting.domain.model.MeetingTranscriptStatus;

/*
 * A 분석 도메인의 공개 요약 상태 계약을 D 회의 도메인의 출력 Port에 연결하는 어댑터다.
 *
 * 중단·실패 판정과 회사 범위 검증은 원본 데이터를 소유한 A가 수행하고 이 경계는 읽기 모델만 변환한다.
 */
@Component
@RequiredArgsConstructor
public class SummaryStatusQueryAdapter implements SummaryStatusQueryPort {

    /* A가 소유하는 회의별 요약 상태 배치 조회 계약이다. */
    private final MeetingSummaryQueryPort meetingSummaryQueryPort;

    /* A의 중단·실패 결과를 D가 소유하는 읽기 모델로 변환한다. */
    @Override
    public List<StalledSummaryMeeting> findStalledSummaries(Long companyId, List<Long> meetingIds) {
        /* 분석 도메인의 반환 타입이 D 서비스까지 전파되지 않도록 경계에서 값만 복사한다. */
        return meetingSummaryQueryPort.findStalledSummaries(companyId, meetingIds)
                .stream()
                .map(summary -> new StalledSummaryMeeting(summary.meetingId(), summary.isStalled()))
                .toList();
    }

    /* A가 접은 전체 요약 상태를 재계산하지 않고 D 응답 enum으로 변환한다. */
    @Override
    public List<SummaryStatusMeeting> findSummaryStatuses(Long companyId, List<Long> meetingIds) {
        return meetingSummaryQueryPort.findSummaryStatuses(companyId, meetingIds)
                .stream()
                .map(summary -> new SummaryStatusMeeting(
                        summary.meetingId(),
                        toMeetingSummaryStatus(summary.status())
                ))
                .toList();
    }

    /* A가 판정한 STT 정본 상태를 D 발화 기록 응답 값으로 복사한다. */
    @Override
    public Optional<TranscriptStatusMeeting> findTranscriptStatus(Long companyId, Long meetingId) {
        return meetingSummaryQueryPort.findTranscriptStatus(companyId, meetingId)
                .map(transcript -> new TranscriptStatusMeeting(
                        transcript.meetingId(),
                        toMeetingTranscriptStatus(transcript.status())
                ));
    }

    /* A 상태가 늘어나면 컴파일 단계에서 D 응답 계약 갱신을 강제하도록 모든 값을 명시적으로 변환한다. */
    private MeetingSummaryStatus toMeetingSummaryStatus(MeetingSummaryQueryPort.SummaryStatus status) {
        return switch (status) {
            case NONE -> MeetingSummaryStatus.NONE;
            case WAITING_TRANSCRIPT -> MeetingSummaryStatus.WAITING_TRANSCRIPT;
            case PROCESSING -> MeetingSummaryStatus.PROCESSING;
            case DONE -> MeetingSummaryStatus.DONE;
            case STALLED -> MeetingSummaryStatus.STALLED;
            case FAILED -> MeetingSummaryStatus.FAILED;
        };
    }

    /* STT 상태도 문자열 변환 없이 명시적으로 복사해 두 도메인의 계약 차이를 즉시 드러낸다. */
    private MeetingTranscriptStatus toMeetingTranscriptStatus(
            MeetingSummaryQueryPort.TranscriptStatus status
    ) {
        return switch (status) {
            case NOT_STARTED -> MeetingTranscriptStatus.NOT_STARTED;
            case PROCESSING -> MeetingTranscriptStatus.PROCESSING;
            case DONE -> MeetingTranscriptStatus.DONE;
            case FAILED -> MeetingTranscriptStatus.FAILED;
        };
    }
}
