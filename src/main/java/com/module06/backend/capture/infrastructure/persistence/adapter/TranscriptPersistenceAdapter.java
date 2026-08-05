package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.domain.model.Utterance;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataTranscriptChunkRepository;

/*
 * transcript_chunk → Utterance 변환이 일어나는 자리다. 컬럼명과 API 계약이 갈리는 지점을
 * 여기 하나로 몰아 둔다(offset_ms → startOffsetMs).
 */
@Repository
@RequiredArgsConstructor
public class TranscriptPersistenceAdapter implements TranscriptRepository {

    private final SpringDataTranscriptChunkRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Utterance> findByMeetingOrderByOffset(long meetingId) {
        return repository.findByMeetingIdOrderByOffsetMsAscSeqAsc(meetingId).stream()
                .map(chunk -> new Utterance(
                        chunk.getId(),
                        chunk.getSpeakerMemberId(),
                        chunk.getOffsetMs(),
                        chunk.getContent()))
                .toList();
    }
}
