package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.TopicSegment;

/* AI-03(L2 주제 분할) 호출 결과. 산출물과 "무엇으로 만들어졌는지"를 함께 들고 온다. */
public record SegmentTopicsResult(
        List<TopicSegment> topics,
        LayerRun run
) {
}
