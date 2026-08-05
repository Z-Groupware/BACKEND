package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.Utterance;

/*
 * AI EC2(Python) 내부 API를 부르는 아웃바운드 포트다.
 *
 * 포트로 두는 이유가 두 가지다.
 *  ① 오케스트레이션 테스트가 실제 HTTP 없이 돈다. 계층 순서·잠금·상태 전이는 이 저장소의 로직이고,
 *     그걸 검증하는 데 Gemini 크레딧이나 네트워크가 필요하면 아무도 테스트를 돌리지 않게 된다.
 *  ② 계층을 특화 모델로 갈아끼우거나 제공자를 바꿔도 이 인터페이스가 계약을 고정한다.
 *
 * 호출은 **단방향**이다 — Python 은 업무 DB에 접속하지 않고, 이쪽이 필요한 문맥을 전부 실어 보낸다.
 * 그래서 utterances 를 매번 통째로 넘긴다(상태 없는 계산 서버).
 *
 * 이 포트가 던지는 실패는 AiLayerException 으로 통일한다. 구현체가 RestClient 예외를 그대로
 * 흘리면 오케스트레이터가 HTTP 라이브러리에 묶이고, 재시도 여부 판정도 그쪽으로 샌다.
 */
public interface AiLayerPort {

    /* AI-03 · L2 주제 분할. 오버랩 3발화는 Python 후처리가 붙여서 돌려준다. */
    SegmentTopicsResult segmentTopics(long tenantId, long meetingId, List<Utterance> utterances);

    /*
     * AI-04 · L3 주제별 정리. 주제마다 한 번씩 부른다(명세 「주제별 N회」).
     *
     * utterances 는 그 주제의 발화만 넘긴다 — 회의 전체를 넘기면 주제를 나눈 의미가 없고
     * 토큰만 주제 수만큼 곱해진다.
     */
    SummarizeTopicResult summarizeTopic(
            long tenantId,
            long meetingId,
            int topicSeq,
            String topic,
            List<Utterance> utterances,
            List<Participant> participants
    );

    /*
     * 계층의 닫힌 목록에 들어갈 참석자다. personId 가 null 인 항목이 unknown_person 탈출구다.
     * 탈출구가 없으면 모델이 명단 안에서 억지로 하나를 고른다(명세 AI-06).
     */
    record Participant(Long personId, String name) {
    }
}
