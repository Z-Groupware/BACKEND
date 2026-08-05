package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.TopicItem;

/* AI-04(L3 주제별 정리) 호출 결과. 주제마다 한 번씩 나온다. */
public record SummarizeTopicResult(
        String summary,
        List<TopicItem> items,
        LayerRun run
) {
}
