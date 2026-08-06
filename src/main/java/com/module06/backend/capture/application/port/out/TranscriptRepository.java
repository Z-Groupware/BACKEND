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
     * **판정된 것만 넘어온다.** 포기한 발화는 목록에 없고, 그 발화의 화자는 손대지 않는다 —
     * NULL 로 덮어쓰지도 않는다. 재실행에서 자막이 줄어들 일은 없으므로 이전 판정을 지울
     * 이유가 없고, 지우면 자막이 일시적으로 안 읽힌 실행 하나가 이전 판정을 날린다.
     *
     * meetingId 를 함께 받는 이유는 회사 스코프다. utteranceId 는 판정 결과에서 온 값이라
     * 그것만으로 갱신하면 다른 회의(다른 회사)의 정본을 고칠 수 있는 경로가 열린다.
     *
     * @return 실제로 이식된 건수. 넘긴 수와 다르면 그 회의에 없는 발화 id 가 섞였다는 뜻이다
     */
    int applySpeakerAttributions(long meetingId, List<Attribution> attributions);
}
