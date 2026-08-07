package com.module06.backend.capture.presentation.api.response;

import com.module06.backend.capture.application.result.AnalysisOutcome;

/*
 * ANLZ-01 응답이다.
 *
 * 명세는 202 · {"status": "QUEUED"} 를 전제하는데 그건 SQS 가 있을 때의 모양이다.
 * 지금은 큐가 없어 요청 스레드에서 그대로 돌므로 **실제 결과**를 담는다 —
 * DONE·SKIPPED·ALREADY_RUNNING·SUPERSEDED·FAILED. QUEUED 로 고정해 내려주면 이미 실패한
 * 분석이 "대기 중"으로 보인다.
 *
 * SUPERSEDED 는 이 요청보다 **나중에 시작한 분석**이 있어 이 실행이 물러난 것이다(#134).
 * 화면에서는 재시도 버튼을 띄우지 않는 쪽이 맞다 — 더 새 입력을 가진 실행이 이미 돌고 있다.
 *
 * failedLayer 를 함께 내려주는 이유: ANLZ-02(계층 재개)가 그 값을 resumeFromLayer 로 받는다.
 * 없으면 사용자가 어느 계층부터 재개할지 스스로 알아내야 한다.
 */
public record AnalysisRunResponse(
        String status,
        String failedLayer,
        String errorCode,
        boolean retryable,
        int topicCount,
        String message
) {

    public static AnalysisRunResponse from(AnalysisOutcome outcome) {
        return new AnalysisRunResponse(
                outcome.status().name(),
                outcome.failedLayer() != null ? outcome.failedLayer().wireValue() : null,
                outcome.errorCode(),
                outcome.retryable(),
                outcome.topicCount(),
                outcome.message());
    }
}
