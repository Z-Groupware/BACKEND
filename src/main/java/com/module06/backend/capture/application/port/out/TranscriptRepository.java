package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.Utterance;

/*
 * transcript_chunk 읽기 포트다.
 *
 * transcript_chunk 는 **공용 테이블**이다(V5.3 주석). 다른 도메인도 쓰므로 이쪽에서 쓰기를
 * 하지 않고, 컬럼명 매핑(offset_ms → startOffsetMs)도 어댑터 안에서 끝낸다.
 */
public interface TranscriptRepository {

    /* 회의의 발화를 시작 오프셋 순서로 읽는다. 순서가 곧 L2 가 자를 기준선이다. */
    List<Utterance> findByMeetingOrderByOffset(long meetingId);
}
