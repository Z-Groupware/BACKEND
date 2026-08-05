package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.RecordingPart;

// RecordingPart 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유
public interface RecordingPartRepository {

    /** 청크 하나를 저장한다 (append-only, 수정 없음) */
    RecordingPart save(RecordingPart recordingPart);
}
