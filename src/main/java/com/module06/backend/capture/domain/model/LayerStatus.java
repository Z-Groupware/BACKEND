package com.module06.backend.capture.domain.model;

/*
 * 계층 실행 상태다(analysis_layer.status).
 *
 * RUNNING 이 중복 실행 잠금 역할을 겸한다 — SQS 는 at-least-once 라 같은 회의에 대한
 * 중복 수신은 언젠가 반드시 온다. UNIQUE(meeting_id, layer) 제약과 함께 쓰면
 * 두 번째 실행은 INSERT 단계에서 막힌다(V5.6 주석 ②).
 *
 * SKIPPED 를 DONE 과 나눠 두는 것이 중요하다. 둘을 합치면 "돌아서 산출물이 없음"과
 * "아예 돌지 않음"이 같은 값이 되고, 품질 지표에서 후자가 전자로 위장된다.
 */
public enum LayerStatus {

    /* 아직 시작하지 않았다. */
    PENDING,

    /* 실행 중. 이 상태가 중복 실행을 막는 잠금이다. */
    RUNNING,

    /* 완료. 산출물이 저장됐다. */
    DONE,

    /* 실패. 계층 단위로 3회 재시도 후 이 상태가 되고 잡은 PAUSED 로 멈춘다. */
    FAILED,

    /* 돌리지 않았다(대상 없음 등). DONE 과 절대 합치지 않는다. */
    SKIPPED;

    /* 재개(ANLZ-02) 대상인가 — 실패했거나 아직 안 돈 계층만 다시 돌린다. */
    public boolean isResumable() {
        return this == FAILED || this == PENDING;
    }
}
