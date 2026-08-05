package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 findById/existsById 등 기본 CRUD를 자동 구현해준다.
// ⚠️ Cap 접두어 이유: meetingroom 도메인의 동명 리포지토리와 Spring 빈 이름(단순명 기준)이 겹치면
//    BeanDefinitionOverrideException으로 컨텍스트가 죽는다. 도메인 프리픽스로 빈 이름을 분리한다.
public interface SpringDataCapMeetingReferenceRepository extends JpaRepository<CapMeetingReferenceEntity, Long> {
}
