package com.module06.backend.handover.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 인계 패키지 조회 + 인사이트("레거시 컴파일러") 조립에 필요한 회의 데이터를
 * 외부(회의 도메인)에서 가져오는 아웃바운드 포트.
 * 구현체는 회의 모듈이 담당하며, 미구현 시 컨텍스트는 정상 부팅한다(Optional/ObjectProvider 주입).
 */
public interface MeetingQueryPort {

    MeetingHistory findMeeting(Long meetingId);

    // --- 인사이트 레이어 증분 계약 ---

    /** 인사이트 조립용: 프로젝트별 회의를 시간순으로. */
    List<ProjectMeeting> findProjectMeetingsOrdered(Long projectId);

    /** 인사이트 조립용: 회의 참석자 배치 조회. */
    List<MeetingAttendee> findMeetingAttendees(List<Long> meetingIds);

    /** 인사이트 조립용: 회의 토픽(맥락 타임라인) 배치 조회. */
    List<MeetingTopic> findMeetingTopics(List<Long> meetingIds);

    record MeetingHistory(
            Long meetingId,
            LocalDate date,
            List<String> attendees,
            String decisionSummary,
            String actionItemsSummary
    ) {
    }

    record ProjectMeeting(
            Long meetingId,
            Long projectId,
            Long hostMemberId,
            String title,
            LocalDateTime startAt
    ) {
    }

    record MeetingAttendee(
            Long meetingId,
            Long memberId
    ) {
    }

    record MeetingTopic(
            Long meetingId,
            Long topicId,
            Long parentTopicId,
            String topicType,
            String content,
            int sortOrder
    ) {
    }
}
