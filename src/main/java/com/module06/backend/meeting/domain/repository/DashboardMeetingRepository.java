package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.DashboardMeetingScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-17 대시보드 최근 회의 카드 조회를 수행하는 도메인 저장소 계약이다.
 *
 * MEET-02와 재료(회사·회의·회의실·프로젝트)는 같지만 조회 범위와 표시 목적이 달라
 * MeetingListRepository를 확장하지 않고 별도 유스케이스·계약으로 둔다.
 */
public interface DashboardMeetingRepository {

    /* 회사·스코프·요청자 조건으로 취소되지 않은 최근 회의를 상한 개수만큼 조회한다. */
    List<DashboardMeetingCandidate> findDashboardMeetings(DashboardMeetingCriteria criteria);

    /* 저장소가 적용할 스코프·요청자·상한 조건이다. */
    record DashboardMeetingCriteria(
            Long companyId,
            DashboardMeetingScope scope,
            Long requesterMemberId,
            Long requesterTeamId,
            int limit
    ) {
    }

    /* 대시보드 카드 조립에 필요한 회의 메타만 담은 읽기 모델이다. */
    record DashboardMeetingCandidate(
            Long meetingId,
            String title,
            Long projectId,
            MeetingStatus status,
            Long meetingRoomId,
            LocalDateTime startAt
    ) {
    }
}
