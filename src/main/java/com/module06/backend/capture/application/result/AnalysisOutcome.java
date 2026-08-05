package com.module06.backend.capture.application.result;

import com.module06.backend.capture.domain.model.LayerName;

/*
 * 분석 실행 한 번의 결과다. ANLZ-01 응답과 로그가 이 값을 그대로 읽는다.
 *
 * 네 가지를 **섞지 않는 것**이 요점이다.
 *
 *   DONE            계층이 돌아 산출물이 저장됐다
 *   SKIPPED         돌릴 대상이 없어 아예 부르지 않았다 (발화 0건 등)
 *   ALREADY_RUNNING 다른 실행이 이미 잡고 있다 — 오류가 아니라 중복이 걸러진 정상 동작
 *   FAILED          계층이 실패했다. 여기서 멈췄고 재개 지점이 남아 있다
 *
 * SKIPPED 를 DONE 으로 합치면 자막·STT 사고가 "분석은 됐는데 결과가 없네"로 보이고,
 * ALREADY_RUNNING 을 FAILED 로 합치면 정상적인 중복 방어가 장애로 보고된다.
 */
public record AnalysisOutcome(
        Status status,
        LayerName failedLayer,
        String errorCode,
        String message,
        boolean retryable,
        int topicCount
) {

    public enum Status {
        DONE, SKIPPED, ALREADY_RUNNING, FAILED
    }

    public static AnalysisOutcome done(int topicCount) {
        return new AnalysisOutcome(Status.DONE, null, null, null, false, topicCount);
    }

    public static AnalysisOutcome skipped(String message) {
        return new AnalysisOutcome(Status.SKIPPED, null, null, message, false, 0);
    }

    public static AnalysisOutcome alreadyRunning(LayerName layer) {
        return new AnalysisOutcome(Status.ALREADY_RUNNING, layer, null,
                "이미 분석이 진행 중입니다.", false, 0);
    }

    public static AnalysisOutcome failed(LayerName layer, String errorCode, String message, boolean retryable) {
        return new AnalysisOutcome(Status.FAILED, layer, errorCode, message, retryable, 0);
    }
}
