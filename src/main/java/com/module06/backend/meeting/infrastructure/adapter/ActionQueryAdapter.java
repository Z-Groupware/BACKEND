package com.module06.backend.meeting.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.action.application.port.MeetingActionQueryPort;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;

/*
 * C 액션 도메인의 공개 조회 계약을 D 회의 도메인의 출력 Port에 연결하는 어댑터다.
 *
 * 분배 판정 기준(dispatched_at IS NULL AND review_status != REJECTED)과 회사 범위는
 * 원본 데이터를 소유한 액션 도메인이 적용하며, 이 경계에서는 두 도메인의 읽기 모델만 변환한다.
 * ProjectQueryAdapter와 동일한 패턴이다.
 */
@Component
@RequiredArgsConstructor
public class ActionQueryAdapter implements ActionQueryPort {

    /* 액션 도메인이 소유하는 존재 검증 및 분배 대기 배치 조회 계약이다. */
    private final MeetingActionQueryPort meetingActionQueryPort;

    /* MEET-01이 회의에 연결하려는 액션이 요청 회사에 존재하는지 확인한다. */
    @Override
    public boolean existsAction(Long companyId, Long actionId) {
        /* 회사 범위 판정은 원본 데이터를 소유한 액션 도메인에 위임한다. */
        return meetingActionQueryPort.existsAction(companyId, actionId);
    }

    /* MEET-10 후보 회의 중 분배 대기 액션이 남은 회의와 그 건수를 한 번에 조회한다. */
    @Override
    public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
            Long companyId,
            List<Long> meetingIds
    ) {
        /* C의 배치 결과를 D가 공개한 읽기 모델로 변환해 액션 구현 타입의 전파를 막는다. */
        return meetingActionQueryPort
                .findMeetingsWithUndispatchedActions(companyId, meetingIds)
                .stream()
                .map(meeting -> new UndispatchedActionMeeting(
                        meeting.sourceMeetingId(),
                        meeting.undispatchedCount()
                ))
                .toList();
    }

    /* MEET-02 현재 페이지의 회의별 전체 액션 수를 C의 배치 계약으로 조회한다. */
    @Override
    public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> meetingIds) {
        /* C의 sourceMeetingId 기반 결과를 D 목록 조립용 읽기 모델로 변환한다. */
        return meetingActionQueryPort
                .countActionsByMeetings(companyId, meetingIds)
                .stream()
                .map(meeting -> new MeetingActionCount(
                        meeting.sourceMeetingId(),
                        meeting.actionCount()
                ))
                .toList();
    }
}
