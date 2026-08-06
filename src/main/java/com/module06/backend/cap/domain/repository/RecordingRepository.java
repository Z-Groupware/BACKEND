package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.Recording;

import java.util.Optional;

// Recording(통합 녹음본 메타) 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유.
public interface RecordingRepository {

    /** 녹음본 메타를 저장한다 */
    Recording save(Recording recording);

    /** 이 회의에 이미 등록된 녹음본이 있는지 — 수동 업로드 중복 제출(409) 판정용. */
    boolean existsByMeetingId(Long meetingId);

    /** 이 회의의 녹음본을 조회한다(회의당 1건, UNIQUE(meeting_id)). 재생 URL 발급용. */
    Optional<Recording> findByMeetingId(Long meetingId);
}
