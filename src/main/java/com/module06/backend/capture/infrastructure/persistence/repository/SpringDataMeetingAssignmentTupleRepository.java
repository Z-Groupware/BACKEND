package com.module06.backend.capture.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAssignmentTupleJpaEntity;

public interface SpringDataMeetingAssignmentTupleRepository
        extends JpaRepository<MeetingAssignmentTupleJpaEntity, Long> {

    /* L4 재실행 시 이전 tuple 을 지운다. 남기면 같은 배정이 두 배로 쌓인다. */
    void deleteByMeetingId(Long meetingId);
}
