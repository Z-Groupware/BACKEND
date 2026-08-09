package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// member 읽기 전용 리포지토리. ⚠️ Cap 접두어로 action/project 도메인의 동명 리포지토리와 빈 이름 충돌 회피.
public interface SpringDataCapMemberReferenceRepository extends JpaRepository<CapMemberReferenceEntity, Long> {
}
