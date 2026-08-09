package com.module06.backend.cap.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 save/findById 등 기본 CRUD를 자동 구현해준다.
public interface SpringDataCaptureUploadStateRepository
        extends JpaRepository<CaptureUploadStateJpaEntity, Long> {

    // 주어진 회의들 중 캡처가 존재하는 가장 최근(created_at 최신) 상태 하나. 한 사람이 여러 진행 중
    // 회의에 겹쳐 있는 드문 경우를 대비해 가장 최근에 시작된 캡처를 고른다. 파생 쿼리(QUERY_002 준수).
    Optional<CaptureUploadStateJpaEntity> findFirstByMeetingIdInOrderByCreatedAtDesc(Collection<Long> meetingIds);
}
