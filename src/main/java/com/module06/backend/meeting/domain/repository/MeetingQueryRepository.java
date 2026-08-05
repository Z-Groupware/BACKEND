package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingTopicType;

/*
 * 회의와 참석자 읽기 모델을 조회하는 도메인 저장소 계약이다.
 *
 * MEET-01의 저장 계약과 RESULT-01·E 인수인계 연동의 조회 계약을 분리해
 * 쓰기 로직이 늘어나도 읽기 전용 요구사항이 저장 애그리거트에 섞이지 않게 한다.
 */
public interface MeetingQueryRepository {

    /* 회사 범위에서 회의 한 건과 참석자 식별자 목록을 조회한다. */
    Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId);

    /* 프로젝트에 속한 회의를 시작 시각과 식별자 오름차순으로 조회한다. */
    List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId);

    /* 인증 사용자가 참석한 예정·진행 중 회의를 현재 시각 이후 기준으로 제한 조회한다. */
    List<UpcomingMeetingSnapshot> findUpcomingMeetings(
            Long companyId,
            Long memberId,
            LocalDateTime now,
            int limit
    );

    /* 회사 범위 안의 여러 회의에 연결된 대주제와 소주제를 한 번에 조회한다. */
    List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds);

    /* 회사 범위 안의 여러 회의에 연결된 참석자 식별자를 한 번에 조회한다. */
    List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds);

    /* RESULT-01 권한 판정과 E 단건 조회에 필요한 회의 읽기 모델이다. */
    record MeetingSnapshot(
            Long meetingId,
            Long companyId,
            Long projectId,
            Long hostMemberId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            List<Long> attendeeMemberIds
    ) {

        /* 외부에서 참석자 목록을 변경하지 못하도록 읽기 모델 생성 시 불변 복사한다. */
        public MeetingSnapshot {
            /* 영속성 어댑터는 항상 목록을 제공하므로 null이면 계약 위반으로 즉시 실패하게 한다. */
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }
    }

    /* E의 프로젝트 회의 타임라인에 제공할 최소 회의 읽기 모델이다. */
    record ProjectMeetingSnapshot(
            Long meetingId,
            String title,
            LocalDateTime startAt,
            Long hostMemberId,
            MeetingStatus status
    ) {
    }

    /* MEET-03 카드 조립에 필요한 회의 메타와 참석자 수를 담은 읽기 모델이다. */
    record UpcomingMeetingSnapshot(
            Long meetingId,
            Long projectId,
            Long meetingRoomId,
            Long hostMemberId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int attendeeCount
    ) {
    }

    /* E의 배치 회의 맥락 조회에 제공할 회의 안건 읽기 모델이다. */
    record MeetingTopicSnapshot(
            Long meetingId,
            MeetingTopicType type,
            String content,
            int sortOrder
    ) {
    }

    /* E의 배치 참석자 조회에 제공할 회의와 구성원 식별자 쌍이다. */
    record MeetingAttendeeReference(Long meetingId, Long memberId) {
    }
}
