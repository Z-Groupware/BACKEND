package com.module06.backend.capture.presentation.api.response;

import java.util.List;

import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.MeetingSummaryView;

/*
 * ANLZ-03 응답이다.
 *
 * gateStatus 를 그대로 내려준다. 이 슬라이스에서는 항상 null 인데, 그것이 "게이트를 아직
 * 안 지났다"는 사실 그대로다. CONFIRMED 로 채워 보내면 화면이 확정된 결정으로 표시하고
 * 사용자가 그것을 분배해 버린다 — L3.5 를 둔 의미가 사라진다.
 */
public record MeetingSummaryResponse(
        String overview,
        List<TopicResponse> topics
) {

    public static MeetingSummaryResponse from(MeetingSummaryView view) {
        return new MeetingSummaryResponse(
                view.overview(),
                view.topics().stream()
                        .map(topic -> new TopicResponse(
                                topic.topicSeq(),
                                topic.topic(),
                                topic.items().stream()
                                        .map(item -> new ItemResponse(
                                                item.id(),
                                                item.itemType().name(),
                                                item.content(),
                                                item.reason(),
                                                item.evidenceUtteranceId(),
                                                item.gateStatus()))
                                        .toList()))
                        .toList());
    }

    public record TopicResponse(int topicSeq, String topic, List<ItemResponse> items) {
    }

    /*
     * evidenceUtteranceId 를 화면까지 내려준다. 근거 발화로 이동할 수 없으면 사용자는
     * 항목이 맞는지 확인할 방법이 없고, 그러면 검토가 형식이 된다.
     */
    public record ItemResponse(Long id, String itemType, String content, String reason,
                               Long evidenceUtteranceId, String gateStatus) {
    }
}
