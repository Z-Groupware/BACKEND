package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.application.service.SpeakerAttributionResolver.Attribution;
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
