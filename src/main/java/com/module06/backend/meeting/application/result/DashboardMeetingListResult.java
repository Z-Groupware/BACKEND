package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-17 조회 결과를 프레젠테이션 계층에 전달하는 애플리케이션 결과 객체다.
 *
 * originLabel·hostLabel은 B의 findTeams 배치 계약이 연결되기 전까지 항상 null이다.
 */
public record DashboardMeetingListResult(List<MeetingItem> meetings) {

    /* 결과 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public DashboardMeetingListResult {
        /* 빈 목록도 허용하되 null은 정상 결과로 숨기지 않고 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 대시보드 최근 회의 카드 한 건을 구성하는 애플리케이션 결과다. */
    public record MeetingItem(
            Long meetingId,
            String title,
            String projectTag,
            MeetingStatus status,
            String room,
            LocalDateTime scheduledAt,
            int attendeeCount,
            String originLabel,
            String hostLabel
    ) {
    }
}
