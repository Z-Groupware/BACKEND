package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 existsById(복합키) 등 기본 CRUD를 자동 구현해준다.
// ⚠️ Cap 접두어 이유: meetingroom 도메인의 동명 리포지토리와 빈 이름이 겹치는 것을 피한다.
public interface SpringDataCapMeetingAttendeeReferenceRepository
        extends JpaRepository<CapMeetingAttendeeReferenceEntity, MeetingAttendeeId> {
}
