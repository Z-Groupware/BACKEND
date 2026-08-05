package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingSummaryJpaEntity;

public interface SpringDataMeetingSummaryRepository extends JpaRepository<MeetingSummaryJpaEntity, Long> {

    Optional<MeetingSummaryJpaEntity> findByMeetingId(Long meetingId);
}
