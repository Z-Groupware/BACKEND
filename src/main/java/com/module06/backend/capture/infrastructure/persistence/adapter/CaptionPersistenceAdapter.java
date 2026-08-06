package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.CaptionRepository;
import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataCaptionChunkRepository;

/*
 * caption_chunk → CaptionChunk 변환 어댑터다. 읽기 전용이다 — 이 테이블은 김현지(CAP) 소유고
 * CAP-11 이 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class CaptionPersistenceAdapter implements CaptionRepository {

    private final SpringDataCaptionChunkRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<CaptionChunk> findByMeeting(long meetingId) {
        return repository.findByMeetingIdOrderByStartOffsetMsAsc(meetingId).stream()
                .map(chunk -> new CaptionChunk(
                        chunk.getMemberId(),
                        chunk.getStartOffsetMs(),
                        chunk.getEndOffsetMs(),
                        chunk.getRms()))
                .toList();
    }
}
