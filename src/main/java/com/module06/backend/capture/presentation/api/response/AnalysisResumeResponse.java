package com.module06.backend.capture.presentation.api.response;

import java.util.List;

import com.module06.backend.capture.application.usecase.ResumeAnalysisUseCase.ResumeOutcome;
import com.module06.backend.capture.domain.model.LayerName;

/*
 * ANLZ-02 응답이다.
 *
 * <h2>status 는 실제 결과다</h2>
 * 명세는 202 · {"status": "QUEUED"} 를 전제하는데 그건 SQS 가 있을 때의 모양이다. 지금은 큐가
 * 없어 요청 스레드에서 그대로 돌므로 ANLZ-01 과 같은 방식으로 실제 결과를 담는다 —
 * QUEUED 로 고정하면 이미 다시 실패한 재시도가 "대기 중"으로 보인다.
 *
 * <h2>reusedLayers 가 이 API 의 값이다</h2>
 * 재개의 요점은 "무엇을 다시 안 태웠는가"다. 이 목록이 없으면 사용자는 앞 계층이 정말
 * 건너뛰어졌는지 응답으로 확인할 수 없고, 알게 되는 것은 청구서에서다.
 */
public record AnalysisResumeResponse(
        String status,
        String resumeFromLayer,
        List<String> reusedLayers,
        String failedLayer,
        String errorCode,
        boolean retryable,
        int topicCount,
        String message
) {

    public static AnalysisResumeResponse from(ResumeOutcome resumed) {
        return new AnalysisResumeResponse(
                resumed.outcome().status().name(),
                resumed.resumeFrom().wireValue(),
                resumed.reusedLayers().stream().map(LayerName::wireValue).toList(),
                resumed.outcome().failedLayer() != null ? resumed.outcome().failedLayer().wireValue() : null,
                resumed.outcome().errorCode(),
                resumed.outcome().retryable(),
                resumed.outcome().topicCount(),
                resumed.outcome().message());
    }
}
