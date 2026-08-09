package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.application.service.SpeakerAttributionResolver.Attribution;
import com.module06.backend.capture.domain.model.SpeakerSource;
import com.module06.backend.capture.domain.model.TranscriptCursor;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * transcript_chunk 접근 포트다.
 *
 * transcript_chunk 는 **공용 테이블**이다(V5.3 주석). 다른 도메인도 쓰므로 쓰기는 L1 이
 * 소유한 화자 두 컬럼(speaker_member_id · speaker_source)에만 한다 — 그 컬럼을 V5.3 이
 * L1 을 위해 추가했고 그 계층이 A 소유다. 정본 내용(content · offset_ms)은 적재하는 쪽
 * 소유라 건드리지 않는다.
 *
 * 컬럼명 매핑(offset_ms → startOffsetMs)은 어댑터 안에서 끝낸다.
 */
public interface TranscriptRepository {

    /* 회의의 발화를 시작 오프셋 순서로 읽는다. 순서가 곧 L2 가 자를 기준선이다. */
    List<Utterance> findByMeetingOrderByOffset(long meetingId);

    /*
     * 정본 스크립트 한 페이지(ANLZ-05).
     *
     * 위의 {@link #findByMeetingOrderByOffset} 를 그대로 쓰지 않는 이유는 그것이 **회의 전체**를
     * 읽기 때문이다. L2 에 정본을 통째로 넘기려고 만든 계약이라 그렇게 되어 있고, 2시간 회의면
     * 발화가 수천 건이라 API 응답에 그대로 실을 수 없다.
     *
     * @param cursor 직전 페이지의 마지막 발화. null 이면 첫 페이지
     * @param limit  최대 건수. 다음 페이지 존재 여부는 호출자가 결과 크기로 판단한다
     */
    List<UtteranceView> findPage(long meetingId, TranscriptCursor cursor, int limit);

    /*
     * 지정한 발화만 골라 읽는다(ANLZ-05 의 {@code ?ids=}). 검토 화면이 근거 발화만 볼 때 쓴다.
     *
     * ⚠ **meetingId 를 함께 건다.** id 만으로 조회하면 다른 회의 — 나아가 다른 회사 — 의 발화
     * 내용을 그대로 인용해 내려주게 된다(#100 과 같은 성질). {@link #existsInMeeting} 이 RVW-03
     * 에서 같은 이유로 회의를 함께 거는 것과 같은 판단이다.
     *
     * 요청한 id 중 그 회의에 없는 것은 **조용히 빠진다.** 404 로 세우지 않는 이유는, 근거 발화가
     * 지워진 액션 하나 때문에 검토 화면 전체가 열리지 않게 되기 때문이다. 없는 것은 없는 대로
     * 화면이 비운다.
     */
    List<UtteranceView> findByMeetingAndIds(long meetingId, List<Long> transcriptIds);

    /*
     * API 표면으로 나가는 발화다. {@link Utterance} 와 나누는 이유 —
     *
     * Utterance 는 **Python 계층에 넘기는 계약**이고 그쪽 스키마가 {@code extra="forbid"} 라
     * 모르는 필드를 422 로 거절한다(Utterance 주석). ANLZ-05 가 필요로 하는 {@code seq} ·
     * {@code speakerSource} 를 거기 더하면 계층 호출이 깨진다. 표면이 다르면 모델도 나눈다.
     */
    record UtteranceView(
            Long transcriptId,
            int seq,
            Long speakerMemberId,
            SpeakerSource speakerSource,
            Integer startOffsetMs,
            Integer endOffsetMs,
            String content
    ) {
    }

    /*
     * 그 발화가 **이 회의의 것인가**(RVW-03).
     *
     * 사람이 근거 발화를 직접 지정하는 경로가 생기면서 필요해졌다. 검증 없이 저장하면 다른
     * 회의 — 나아가 **다른 회사** — 의 발화 id 를 넣을 수 있고, 검토 화면은 그 id 로 원문을
     * 조인해 보여준다. 즉 남의 회의 발화 내용이 우리 화면에 인용된다(#100 과 같은 성질이다).
     *
     * 존재 여부만 돌려준다 — 내용을 읽어오면 그 자체가 유출 경로가 된다.
     */
    boolean existsInMeeting(long meetingId, long transcriptId);

    /*
     * L1 판정을 정본에 이식한다.
     *
     * <h2>이번 판정이 그 회의의 화자 상태 전부다</h2>
     * 넘어온 목록에 **없는** 발화의 화자 두 컬럼은 NULL 로 되돌린다. 판정된 것만 덮어쓰는
     * 것으로는 부족하다 —
     *
     *   1. 자막이 더 도착해 2등이 올라오면 1·2등 차이가 3dB 아래로 좁아진다
     *   2. resolver 는 그 발화를 기권한다(동전 던지기를 하지 않는다)
     *   3. 그런데 **예전 판정이 컬럼에 그대로 남는다**
     *   4. L1.5·L4 는 그 값을 확정된 화자로 읽는다
     *
     * 즉 근거가 약해졌다는 사실이 반영되지 않고, 불확실해진 화자가 확정으로 굳는다.
     * 새 근거로 기권한 것이면 컬럼도 기권 상태여야 한다.
     *
     * 지우는 것과 쓰는 것이 **한 트랜잭션**이어야 한다. 나누면 지운 뒤 쓰기가 실패했을 때
     * 화자가 통째로 사라진 회의가 남는다.
     *
     * ⚠ 이 계약은 호출자가 그 회의의 발화 **전체**를 판정에 넣었다는 전제 위에 있다.
     * 일부만 판정해 넘기면 나머지의 화자가 지워진다. 오케스트레이터는 항상 전체를 넣는다.
     *
     * 되돌리는 범위는 L1 소유 두 컬럼뿐이다 — 공용 테이블이므로 정본 내용은 건드리지 않는다.
     *
     * meetingId 를 함께 받는 이유는 회사 스코프다. utteranceId 는 판정 결과에서 온 값이라
     * 그것만으로 갱신하면 다른 회의(다른 회사)의 정본을 고칠 수 있는 경로가 열린다.
     *
     * @return 실제로 이식된 건수. 넘긴 수와 다르면 그 회의에 없는 발화 id 가 섞였다는 뜻이다
     */
    int applySpeakerAttributions(long meetingId, List<Attribution> attributions);
}
