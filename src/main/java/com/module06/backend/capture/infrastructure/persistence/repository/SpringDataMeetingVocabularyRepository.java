package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingVocabularyJpaEntity;

/* meeting_vocabulary 접근. 회의당 하나다(UNIQUE(meeting_id)). */
public interface SpringDataMeetingVocabularyRepository
        extends JpaRepository<MeetingVocabularyJpaEntity, Long> {

    Optional<MeetingVocabularyJpaEntity> findByMeetingId(long meetingId);
}
