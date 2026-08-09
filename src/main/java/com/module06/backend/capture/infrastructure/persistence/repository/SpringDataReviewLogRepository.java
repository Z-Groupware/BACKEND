package com.module06.backend.capture.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.ReviewLogJpaEntity;

/* review_log 접근. append-only 라 조회 메서드를 두지 않는다 — 꺼내 쓰는 것은 AI-08·09 다. */
public interface SpringDataReviewLogRepository extends JpaRepository<ReviewLogJpaEntity, Long> {
}
