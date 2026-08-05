package com.module06.backend.meeting.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;

/*
 * meeting 테이블 저장을 수행하는 Spring Data JPA 기술 저장소다.
 */
public interface SpringDataMeetingRepository extends JpaRepository<MeetingJpaEntity, Long> {
}
