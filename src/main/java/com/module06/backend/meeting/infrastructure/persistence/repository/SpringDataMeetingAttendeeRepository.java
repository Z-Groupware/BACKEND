package com.module06.backend.meeting.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeId;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;

/*
 * meeting_attendee 저장과 검증 조회를 수행하는 Spring Data JPA 기술 저장소다.
 */
public interface SpringDataMeetingAttendeeRepository
        extends JpaRepository<MeetingAttendeeJpaEntity, MeetingAttendeeId> {

    /* 특정 회의에 저장된 참석자 행을 구성원 식별자 순서로 조회한다. */
    List<MeetingAttendeeJpaEntity> findAllByMeetingIdOrderByMemberIdAsc(Long meetingId);
}
