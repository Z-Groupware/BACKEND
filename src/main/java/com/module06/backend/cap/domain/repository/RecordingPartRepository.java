package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.RecordingPart;

import java.util.List;

// RecordingPart 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유
public interface RecordingPartRepository {

    /** 청크 하나를 저장한다 (append-only, 수정 없음) */
    RecordingPart save(RecordingPart recordingPart);

    /** 특정 세그먼트에서 업로드 기록(행)이 존재하는 청크 순번 목록 — missingSeqs 계산용(상태 무관). */
    List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq);
}
