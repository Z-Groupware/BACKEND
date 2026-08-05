package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 findById/existsById 등 기본 CRUD를 자동 구현해준다.
public interface SpringDataMeetingReferenceRepository extends JpaRepository<MeetingReferenceEntity, Long> {
}
