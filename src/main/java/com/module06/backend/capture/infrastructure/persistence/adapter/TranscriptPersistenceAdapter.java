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

    /* 근거 발화가 이 회의의 것인지만 본다(RVW-03). 내용을 읽지 않는 것이 요점이다 — 읽어오면
       확인하려던 유출 경로를 여기서 열게 된다. */
    @Override
    @Transactional(readOnly = true)
    public boolean existsInMeeting(long meetingId, long transcriptId) {
        return repository.existsByIdAndMeetingId(transcriptId, meetingId);
    }

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
     *
     * <h2>회의 전체를 먼저 비우고 판정된 것만 쓴다</h2>
     * 이번 판정이 그 회의의 화자 상태 전부다(포트 주석). 판정된 것만 덮어쓰면, 자막이 더
     * 도착해 근거가 약해져 **기권한** 발화에 예전 판정이 그대로 남는다 — 불확실해진 화자가
     * 확정으로 굳고 L1.5·L4 가 그걸 확정된 화자로 읽는다.
     *
     * 같은 @Transactional 안이라 비우기와 쓰기가 함께 커밋된다. 나누면 지운 뒤 쓰기가 실패했을
     * 때 화자가 통째로 사라진 회의가 남는다.
     *
     * 회의 전체를 읽는 비용을 감수한다 — 어차피 바로 앞에서 findByMeetingOrderByOffset 으로
     * 같은 행을 읽었고, 판정 대상만 읽으면 "지워야 할 행"을 알 방법이 없다.
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

        // meetingId 로만 읽는다 — 판정 결과의 id 로 범위를 잡으면 이번에 기권한 발화가
        // 조회에서 빠져 예전 판정이 남는다. 그게 고치려는 문제 자체다.
        List<TranscriptChunkJpaEntity> rows = repository.findByMeetingId(
                meetingId, SpringDataTranscriptChunkRepository.ORDER);

        int applied = 0;
        for (TranscriptChunkJpaEntity row : rows) {
            Attribution attribution = byUtteranceId.get(row.getId());
            if (attribution != null) {
                row.attributeSpeaker(attribution.speakerMemberId(), attribution.speakerSource());
                applied++;
            } else {
                // 이번 판정에 없는 발화다. 기권했거나 애초에 판정 대상이 아니었다 —
                // 어느 쪽이든 화자를 확정할 근거가 지금은 없다는 뜻이다.
                row.clearSpeaker();
            }
        }
        return applied;
    }
}
