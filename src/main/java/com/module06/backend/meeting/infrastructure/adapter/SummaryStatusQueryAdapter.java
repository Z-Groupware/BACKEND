package com.module06.backend.meeting.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;

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
}
