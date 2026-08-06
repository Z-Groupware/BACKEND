package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.service.SpeakerAttributionResolver.Attribution;
import com.module06.backend.capture.domain.model.Utterance;
import com.module06.backend.capture.infrastructure.persistence.entity.TranscriptChunkJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataTranscriptChunkRepository;

/*
 * transcript_chunk → Utterance 변환이 일어나는 자리다. 컬럼명과 API 계약이 갈리는 지점을
 * 여기 하나로 몰아 둔다(offset_ms → startOffsetMs).
 *
 * 쓰기는 L1 화자 귀속 두 컬럼에만 한다 — 공용 테이블이므로 정본 내용은 건드리지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class TranscriptPersistenceAdapter implements TranscriptRepository {

    private final SpringDataTranscriptChunkRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Utterance> findByMeetingOrderByOffset(long meetingId) {
        return repository.findByMeetingId(meetingId, SpringDataTranscriptChunkRepository.ORDER).stream()
                .map(chunk -> new Utterance(
                        chunk.getId(),
                        chunk.getSpeakerMemberId(),
                        chunk.getOffsetMs(),
                        chunk.getEndOffsetMs(),
                        chunk.getContent()))
                .toList();
    }

    /*
     * L1 판정을 이식한다. 조회한 엔티티를 고치고 트랜잭션 종료 시 더티 체킹으로 반영한다 —
     * 벌크 UPDATE 로 하면 신규 @Query 가 필요하고(QUERY_002 금지), 이식 건수를 세려면 어차피
     * 행을 읽어야 한다.
     */
    @Override
    @Transactional
    public int applySpeakerAttributions(long meetingId, List<Attribution> attributions) {
        Map<Long, Attribution> byUtteranceId = new LinkedHashMap<>();
        for (Attribution attribution : attributions) {
            if (attribution.utteranceId() != null && attribution.speakerMemberId() != null) {
                byUtteranceId.put(attribution.utteranceId(), attribution);
            }
        }
        if (byUtteranceId.isEmpty()) {
            return 0;
        }

        // meetingId 를 조건에 함께 넣는다 — 판정 결과의 id 만으로 갱신하면 다른 회의(다른 회사)의
        // 정본을 고칠 수 있다. 조회에 없는 id 는 그냥 반영되지 않는다.
        List<TranscriptChunkJpaEntity> rows =
                repository.findByMeetingIdAndIdIn(meetingId, byUtteranceId.keySet());

        int applied = 0;
        for (TranscriptChunkJpaEntity row : rows) {
            Attribution attribution = byUtteranceId.get(row.getId());
            if (attribution != null) {
                row.attributeSpeaker(attribution.speakerMemberId(), attribution.speakerSource());
                applied++;
            }
        }
        return applied;
    }
}
