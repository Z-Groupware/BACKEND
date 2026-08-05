package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 save/findById 등 기본 CRUD를 자동 구현해준다.
public interface SpringDataCaptureUploadStateRepository
        extends JpaRepository<CaptureUploadStateJpaEntity, Long> {
}
