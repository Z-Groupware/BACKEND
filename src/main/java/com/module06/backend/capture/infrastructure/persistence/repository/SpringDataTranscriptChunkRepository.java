package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.TranscriptChunkJpaEntity;

public interface SpringDataTranscriptChunkRepository extends JpaRepository<TranscriptChunkJpaEntity, Long> {

    /*
     * 정본 조회 정렬 규약.
     *
     * offset_ms 는 NULL 을 허용한다. MySQL 에서 NULL 은 오름차순 정렬의 **맨 앞**으로 가는데,
     * 그 발화들이 회의 앞부분이라는 보장이 없다. 오프셋이 없는 발화가 앞머리에 붙으면 L2 에
     * 넘기는 정본의 시작이 엉키고 주제 분할 기준선이 통째로 어긋난다.
     *
     * 그래서 NULL 을 **맨 뒤로** 보낸다 — 위치를 아는 발화가 먼저 시간순으로 오고, 모르는 것은
     * 뒤에 붙는다. 앞부분이 정확한 쪽이 낫다.
     *
     * seq 는 2차 정렬이다. 오프셋이 같거나 비어 있어도 적재 순서가 유지된다.
     *
     * 정렬을 Sort 로 넘기는 이유: NULLS LAST 는 메서드 이름으로 표현할 수 없고, JPQL 로 쓰면
     * QUERY_002(신규 @Query 금지)에 걸린다. Sort.Order#nullsLast 가 같은 결과를 규칙 안에서 준다.
     */
    Sort ORDER = Sort.by(
            Sort.Order.asc("offsetMs").nullsLast(),
            Sort.Order.asc("seq"));

    /*
     * 조회와 L1 이식이 **같은 메서드를 쓴다.**
     *
     * 이식에 판정 대상만 골라 읽는 메서드를 따로 두지 않는다. 그러면 이번에 기권한 발화가
     * 조회에서 빠져 예전 판정이 컬럼에 그대로 남는다 — 근거가 약해졌는데 화자는 확정으로
     * 굳는 경로다(TranscriptRepository#applySpeakerAttributions 주석).
     *
     * meetingId 로 범위를 잡는 것이 회사 스코프이기도 하다. 판정 결과가 실어 온 id 만으로
     * 갱신하면 다른 회의(다른 회사)의 정본에 화자를 심을 수 있는데, 갱신은 성공하므로
     * 아무도 오류를 못 본다.
     */
    List<TranscriptChunkJpaEntity> findByMeetingId(Long meetingId, Sort sort);

    /*
     * 근거 발화가 그 회의의 것인지 본다(RVW-03).
     *
     * **id 만으로 찾지 않는다.** 그러면 다른 회의(다른 회사)의 발화도 존재한다고 답하고,
     * 그 id 가 액션에 박혀 검토 화면이 남의 회의 원문을 인용하게 된다. meetingId 를 함께
     * 거는 것이 여기서는 곧 회사 스코프다 — findByMeetingId 와 같은 이유다.
     */
    boolean existsByIdAndMeetingId(Long id, Long meetingId);

    /*
     * ── ANLZ-05 정본 조회 · 커서 페이징 ────────────────────────────────────────
     *
     * <h2>NULL 을 정렬에 맡기지 않고 구간을 나눈다</h2>
     * 위 {@link #ORDER} 는 {@code Sort.Order#nullsLast} 로 "오프셋 없는 발화를 맨 뒤로"를
     * 표현한다. 전체를 한 번에 읽는 경로에서는 그것으로 충분했다.
     *
     * 페이징은 사정이 다르다. NULLS LAST 가 실제 SQL 에 어떻게 나가는지는 조회 실행 경로에
     * 달려 있고(파생 쿼리는 Criteria 로 만들어진다), MySQL 의 기본은 **NULL 이 오름차순의 맨
     * 앞**이다. 어긋나면 꼬리 구간이 첫 페이지로 튀어나오는데, 전체 조회에서는 순서만 이상하고
     * 끝나지만 페이징에서는 **발화가 빠지거나 겹쳐 나간다** — 커서가 가리키는 자리가 옮겨지기
     * 때문이다.
     *
     * 그래서 정렬에 맡기지 않고 {@code offset_ms IS NOT NULL} 구간과 {@code IS NULL} 구간을
     * 따로 뜬 뒤 이어 붙인다. 각 구간의 정렬 키에는 NULL 이 없으므로 결과가 실행 경로에
     * 흔들리지 않는다. "NULL 은 맨 뒤"가 정렬 옵션이 아니라 **조회 순서 자체**로 표현된다.
     *
     * 인덱스는 {@code IX_TRANSCRIPT_CHUNK_MEETING_OFFSET(meeting_id, offset_ms)} 를 탄다(V5.3).
     */

    /* 본문 구간(오프셋 있음) 정렬. NULL 이 섞이지 않아 null 처리 옵션이 필요 없다. */
    Sort OFFSET_THEN_SEQ = Sort.by(Sort.Order.asc("offsetMs"), Sort.Order.asc("seq"));

    /* 꼬리 구간(오프셋 없음) 정렬. 위치를 모르므로 적재 순서만 남는다. */
    Sort SEQ_ONLY = Sort.by(Sort.Order.asc("seq"));

    /* 본문 구간 첫 페이지. */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndOffsetMsIsNotNull(Long meetingId, Pageable pageable);

    /*
     * 블록 하나가 만든 발화를 지운다(적재 교체).
     *
     * sttBlockSeq 를 조건에 넣으므로 NULL 인 행(이 경로 전에 들어온 발화)은 지워지지 않는다 —
     * 파생 쿼리의 `=` 비교가 NULL 과 일치하지 않기 때문이고, 그게 의도한 동작이다.
     */
    void deleteByMeetingIdAndSttBlockSeq(Long meetingId, Integer sttBlockSeq);

    /*
     * 그 회의의 가장 큰 seq. 적재가 그다음 번호부터 붙인다.
     *
     * seq 는 **오프셋이 같을 때의 tie-breaker** 라 회의 안에서 유일하면 된다(커서 페이징이 그
     * 자리에서만 비교한다). 블록을 재처리해 뒤늦게 큰 번호가 붙어도 정렬은 offset_ms 가 하므로
     * 페이징이 깨지지 않는다.
     */
    Optional<TranscriptChunkJpaEntity> findTopByMeetingIdOrderBySeqDesc(Long meetingId);

    /* 본문 구간에서 커서보다 **뒤쪽 오프셋**. 커서와 같은 오프셋은 아래 메서드가 맡는다. */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndOffsetMsGreaterThan(
            Long meetingId, Integer offsetMs, Pageable pageable);

    /*
     * 커서와 **같은 오프셋**에 남은 발화. 이 조회가 없으면 오프셋이 같은 발화가 여럿일 때
     * 페이지 경계에서 나머지가 통째로 사라진다 — 커서를 두 값으로 둔 이유가 여기서 쓰인다.
     */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndOffsetMsAndSeqGreaterThan(
            Long meetingId, Integer offsetMs, Integer seq, Pageable pageable);

    /* 꼬리 구간 시작. 본문을 다 보낸 뒤 이어진다. */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndOffsetMsIsNull(Long meetingId, Pageable pageable);

    /* 꼬리 구간 이어받기. */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndOffsetMsIsNullAndSeqGreaterThan(
            Long meetingId, Integer seq, Pageable pageable);

    /*
     * 근거 발화 선택 조회(ANLZ-05 의 {@code ?ids=}).
     *
     * **id 만으로 찾지 않는다** — existsByIdAndMeetingId 와 같은 이유다. 그쪽은 존재 여부만
     * 답했지만 이쪽은 내용을 실어 보내므로, 회의를 함께 걸지 않으면 남의 회사 발화 원문이
     * 그대로 응답에 실린다.
     */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndIdIn(Long meetingId, Collection<Long> ids, Sort sort);
}
