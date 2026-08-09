package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.CaptionChunk;

import java.util.List;

// CaptionChunk(caption_chunk, V5.2) 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유.
public interface CaptionChunkRepository {

    /**
     * 배치를 저장한다. 이미 존재하는 (meetingId, memberId, seq) 조합은 재전송으로 보고 조용히 건너뛰고,
     * 새로 저장된 조각만 반환한다(재전송에 안전한 멱등 저장 — DB UNIQUE 제약이 최종 판정 근거).
     */
    List<CaptionChunk> saveAllSkippingDuplicates(List<CaptionChunk> chunks);

    /** 이 회의의 자막 전체를 시간순(발화 시작 오프셋)으로 조회한다(CAP-12 백필용). */
    List<CaptionChunk> findByMeetingId(Long meetingId);
}
