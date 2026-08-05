package com.module06.backend.capture.domain.model;

import java.util.List;

/*
 * L2 가 나눈 주제 구간 하나다.
 *
 * utteranceIds 와 (start·end) 를 따로 두는 것이 핵심이다.
 *
 *   start·end   이 주제의 **고유 구간**. 겹치지 않는다. meeting_decision.topic_seq 의 근거다.
 *   utteranceIds  L3 에 실제로 넘길 발화. 앞 주제 끝 3발화(오버랩)가 얹혀 있다.
 *
 * 둘을 같은 값으로 두면 오버랩 구간이 다음 주제의 고유 구간으로 기록되고, 그 안의 결정이
 * 두 주제에 각각 저장된다 — 사용자 화면에서는 같은 결정이 두 번 나온다.
 */
public record TopicSegment(
        int topicSeq,
        String topic,
        Long startUtteranceId,
        Long endUtteranceId,
        List<Long> utteranceIds
) {
}
