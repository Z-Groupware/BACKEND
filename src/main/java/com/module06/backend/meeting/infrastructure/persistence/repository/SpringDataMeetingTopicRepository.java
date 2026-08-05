package com.module06.backend.meeting.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;

/*
 * meeting_topic의 저장과 배치 조회를 수행하는 Spring Data JPA 기술 저장소다.
 */
public interface SpringDataMeetingTopicRepository extends JpaRepository<MeetingTopicJpaEntity, Long> {

    /* 여러 회의의 안건을 회의·표시 순서·식별자 오름차순으로 한 번에 조회한다. */
    List<MeetingTopicJpaEntity> findAllByMeetingIdInOrderByMeetingIdAscSortOrderAscIdAsc(List<Long> meetingIds);
}
