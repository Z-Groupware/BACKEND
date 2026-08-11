package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.service.SpeakerAttributionResolver.Attribution;
import com.module06.backend.capture.domain.model.TranscriptCursor;
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
     * 정본 한 페이지를 뜬다(ANLZ-05).
     *
     * <h2>본문 → 꼬리 순으로 이어 붙인다</h2>
     * 오프셋 있는 발화(본문)를 먼저 다 보내고, 오프셋 없는 발화(꼬리)를 뒤에 붙인다. 정렬
     * 옵션의 NULLS LAST 에 기대지 않고 조회를 나눠 순서를 고정하는 이유는 리포지터리 주석에 있다.
     *
     * <h2>한 페이지가 두 구간에 걸칠 수 있다</h2>
     * 본문이 limit 을 못 채우면 남은 만큼을 꼬리에서 채운다. 걸치는 페이지를 허용하지 않으면
     * 구간 경계마다 빈자리가 생겨, 클라이언트가 "덜 왔는데 다음 커서가 있는" 응답을 계속 받는다.
     *
     * <h2>같은 오프셋을 두 번 조회한다</h2>
     * 커서와 오프셋이 같은 발화(남은 것)와 오프셋이 더 뒤인 발화를 따로 뜬 뒤 이어 붙인다.
     * 하나의 파생 메서드로는 {@code offset > o OR (offset = o AND seq > s)} 를 표현할 수 없고,
     * JPQL 로 쓰면 QUERY_002(신규 @Query 금지)에 걸린다. 두 번 뜨는 대신 규칙 안에 남는다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UtteranceView> findPage(long meetingId, TranscriptCursor cursor, int limit) {
        List<TranscriptChunkJpaEntity> page = new ArrayList<>();

        if (cursor == null || !cursor.isInNullOffsetTail()) {
            page.addAll(bodySegment(meetingId, cursor, limit));
        }
        if (page.size() < limit) {
            page.addAll(tailSegment(meetingId, cursor, limit - page.size()));
        }
        return page.stream().map(TranscriptPersistenceAdapter::toView).toList();
    }

    /* 오프셋 있는 구간. 커서가 없으면 처음부터, 있으면 그 자리 다음부터. */
    private List<TranscriptChunkJpaEntity> bodySegment(long meetingId, TranscriptCursor cursor, int limit) {
        Pageable window = PageRequest.of(0, limit, SpringDataTranscriptChunkRepository.OFFSET_THEN_SEQ);
        if (cursor == null) {
            return repository.findByMeetingIdAndOffsetMsIsNotNull(meetingId, window);
        }

        // 같은 오프셋의 나머지가 먼저다 — 정렬이 (offset, seq) 이므로 그것이 바로 다음 자리다.
        List<TranscriptChunkJpaEntity> sameOffset = repository.findByMeetingIdAndOffsetMsAndSeqGreaterThan(
                meetingId, cursor.offsetMs(), cursor.seq(),
                PageRequest.of(0, limit, SpringDataTranscriptChunkRepository.SEQ_ONLY));
        if (sameOffset.size() >= limit) {
            return sameOffset;
        }

        List<TranscriptChunkJpaEntity> merged = new ArrayList<>(sameOffset);
        merged.addAll(repository.findByMeetingIdAndOffsetMsGreaterThan(
                meetingId, cursor.offsetMs(),
                PageRequest.of(0, limit - sameOffset.size(),
                        SpringDataTranscriptChunkRepository.OFFSET_THEN_SEQ)));
        return merged;
    }

    /* 오프셋 없는 꼬리 구간. 커서가 아직 본문에 있으면 처음부터 채운다. */
    private List<TranscriptChunkJpaEntity> tailSegment(long meetingId, TranscriptCursor cursor, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Pageable window = PageRequest.of(0, limit, SpringDataTranscriptChunkRepository.SEQ_ONLY);
        if (cursor != null && cursor.isInNullOffsetTail()) {
            return repository.findByMeetingIdAndOffsetMsIsNullAndSeqGreaterThan(meetingId, cursor.seq(), window);
        }
        return repository.findByMeetingIdAndOffsetMsIsNull(meetingId, window);
    }

    /*
     * 지정한 발화만 읽는다(ANLZ-05 의 ?ids=). 회의를 함께 걸어 남의 회의 발화가 섞이지 않게 한다.
     *
     * 응답 순서는 요청한 id 순이 아니라 **정본 순서**다. 근거 발화 서넛을 나열할 때도 회의에서
     * 오간 순서로 보이는 편이 읽힌다 — 요청 배열 순서는 화면이 정한 우연한 순서일 뿐이다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UtteranceView> findByMeetingAndIds(long meetingId, List<Long> transcriptIds) {
        if (transcriptIds == null || transcriptIds.isEmpty()) {
            return List.of();
        }
        return repository.findByMeetingIdAndIdIn(
                        meetingId, transcriptIds, SpringDataTranscriptChunkRepository.ORDER).stream()
                .map(TranscriptPersistenceAdapter::toView)
                .toList();
    }

    private static UtteranceView toView(TranscriptChunkJpaEntity chunk) {
        return new UtteranceView(
                chunk.getId(),
                chunk.getSeq(),
                chunk.getSpeakerMemberId(),
                chunk.getSpeakerSource(),
                chunk.getOffsetMs(),
                chunk.getEndOffsetMs(),
                chunk.getContent());
    }

    /*
     * 블록 하나의 정본을 갈아 끼운다.
     *
     * 지우고 넣는 것이 **한 트랜잭션**이어야 한다. 나뉘면 지운 뒤 넣기 전에 분석이 끼어들어
     * 그 구간이 통째로 빈 정본을 읽는다 — 앞뒤가 이어지지 않는 요약이 나오고, 그게 완료로
     * 기록된다.
     *
     * flush 를 명시한다. delete 와 insert 가 같은 (meeting_id, stt_block_seq) 를 건드리므로
     * Hibernate 의 쓰기 순서에 맡기면 insert 가 먼저 나가 같은 구간이 두 벌 존재하는 순간이
     * 생길 수 있다.
     */
    @Override
    @Transactional
    public int replaceBlockTranscript(long meetingId, int sttBlockSeq, List<NewUtterance> utterances) {
        repository.deleteByMeetingIdAndSttBlockSeq(meetingId, sttBlockSeq);
        repository.flush();

        if (utterances == null || utterances.isEmpty()) {
            /*
             * 인식 결과가 비었다. 지우기만 하고 끝낸다 — 침묵 구간이거나 인식이 아무것도 못
             * 건진 블록이고, 둘 다 "그 구간에 발화가 없다"가 맞다. 예외로 올리면 블록 하나가
             * 회의 전체의 적재를 막는다.
             */
            return 0;
        }

        int seq = repository.findTopByMeetingIdOrderBySeqDesc(meetingId)
                .map(TranscriptChunkJpaEntity::getSeq)
                .orElse(0);

        List<TranscriptChunkJpaEntity> rows = new ArrayList<>(utterances.size());
        for (NewUtterance utterance : utterances) {
            rows.add(TranscriptChunkJpaEntity.fromStt(meetingId, ++seq, utterance.text(),
                    utterance.startOffsetMs(), utterance.endOffsetMs(), sttBlockSeq));
        }
        repository.saveAll(rows);
        return rows.size();
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
