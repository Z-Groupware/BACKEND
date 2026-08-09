package com.module06.backend.meeting.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.module06.backend.meeting.application.result.MeetingAttendeeReferenceResult;
import com.module06.backend.meeting.application.result.MeetingHistoryResult;
import com.module06.backend.meeting.application.result.MeetingTopicResult;
import com.module06.backend.meeting.application.result.ProjectMeetingHistoryResult;

/*
 * E 인수인계와 C 프로젝트 도메인이 D 회의 데이터를 읽을 때 사용하는 공개 인바운드 Port다.
 *
 * 소비 도메인은 이 계약과 D 소유 결과만 참조하고 회의 엔티티나 JPA 모델을 직접 참조하지 않는다.
 * 모든 조회는 companyId를 함께 받아 테넌트 경계를 강제한다.
 */
public interface MeetingQueryPort {

    /* 회사 범위에서 출처 회의 한 건과 참석자 표시 정보를 조회한다. */
    Optional<MeetingHistoryResult> findMeeting(Long companyId, Long meetingId);

    /* 프로젝트에 속한 회의를 예약 시작 시각과 회의 식별자 오름차순으로 조회한다. */
    List<ProjectMeetingHistoryResult> findProjectMeetingsOrdered(Long companyId, Long projectId);

    /* 회사 범위의 여러 프로젝트에 연결된 취소되지 않은 회의 수를 프로젝트별로 일괄 조회한다. */
    Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds);

    /* 여러 회의의 대주제와 소주제를 회사 범위에서 일괄 조회한다. */
    List<MeetingTopicResult> findMeetingTopics(Long companyId, List<Long> meetingIds);

    /* 여러 회의의 참석자 식별자 쌍을 회사 범위에서 일괄 조회한다. */
    List<MeetingAttendeeReferenceResult> findMeetingAttendees(Long companyId, List<Long> meetingIds);
}
