package com.module06.backend.capture.application.result;

import com.module06.backend.capture.domain.model.LayerName;

/*
 * 분석 실행 한 번의 결과다. ANLZ-01 응답과 로그가 이 값을 그대로 읽는다.
 *
 * 다섯 가지를 **섞지 않는 것**이 요점이다.
 *
 *   DONE            계층이 돌아 산출물이 저장됐다
 *   SKIPPED         돌릴 대상이 없어 아예 부르지 않았다 (발화 0건 등)
 *   ALREADY_RUNNING 다른 실행이 이미 잡고 있다 — 오류가 아니라 중복이 걸러진 정상 동작
 *   SUPERSEDED      더 나중에 시작한 실행이 있어 물러났다 (#134)
 *   FAILED          계층이 실패했다. 여기서 멈췄고 재개 지점이 남아 있다
 *
 * SKIPPED 를 DONE 으로 합치면 자막·STT 사고가 "분석은 됐는데 결과가 없네"로 보이고,
 * ALREADY_RUNNING 을 FAILED 로 합치면 정상적인 중복 방어가 장애로 보고된다.
 *
 * SUPERSEDED 를 ALREADY_RUNNING 과 합치지 않는 이유 — 뜻이 반대다. ALREADY_RUNNING 은
 * "지금 다른 실행이 돌고 있으니 기다리면 결과가 나온다"이고, SUPERSEDED 는 **이 실행이 이미
 * 낡았다**는 뜻이다. 합치면 SQS 중복과 순서 역전이 같은 신호가 되어, #134 가 실제로 걸리는
 * 것을 아무도 관측하지 못한다.
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
        DONE, SKIPPED, ALREADY_RUNNING, SUPERSEDED, FAILED
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

    /*
     * 더 나중에 시작한 실행이 있어 물러났다.
     *
     * retryable 이 false 다 — 다시 부를 이유가 없다. 이 실행이 하려던 일은 더 새 입력을 가진
     * 실행이 이미 하고 있고, 재시도하면 그 실행과 다시 순서를 다투기만 한다.
     *
     * @param layer 물러난 계층. 실행 번호 발급 단계에서 물러났으면 아직 계층이 없어 null 이다.
     */
    public static AnalysisOutcome superseded(LayerName layer) {
        return new AnalysisOutcome(Status.SUPERSEDED, layer, null,
                "더 나중에 시작한 분석이 있어 이 실행은 중단되었습니다.", false, 0);
    }

    public static AnalysisOutcome failed(LayerName layer, String errorCode, String message, boolean retryable) {
        return new AnalysisOutcome(Status.FAILED, layer, errorCode, message, retryable, 0);
    }
}
