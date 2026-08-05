package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.CaptureUploadState;

import java.util.Optional;

// CaptureUploadState 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유
public interface CaptureUploadStateRepository {

    /** 회의당 1행뿐인 상태를 조회 (없으면 아직 presign 호출된 적 없음) */
    Optional<CaptureUploadState> findByMeetingId(Long meetingId);

    /** 상태 저장(신규/갱신 둘 다) */
    CaptureUploadState save(CaptureUploadState state);
}
