package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

/*
 * analysis_layer(V5.6) 접근 포트다. 이 테이블 하나가 세 가지를 동시에 떠받친다 —
 * 계층 재개(ANLZ-02) · 중복 실행 방지 · 비용 기준선(QLTY-03).
 */
public interface AnalysisLayerRepository {

    /*
     * 계층을 RUNNING 으로 잠근다. **이미 RUNNING 이면 false** 를 돌려주고 아무것도 하지 않는다.
     *
     * 이게 중복 호출 방지의 실체다. SQS 는 at-least-once 라 같은 회의에 대한 중복 수신은
     * 언젠가 반드시 오고, 그때 두 번째 실행이 그대로 돌면 같은 회의에 토큰을 두 배로 태운다.
     * UNIQUE(meeting_id, layer) 위에서 조건부로 잡으므로 애플리케이션 락이 필요 없다.
     *
     * @return 이번 호출이 잠금을 획득했으면 true
     */
    boolean tryLock(long meetingId, LayerName layer);

    /* 성공으로 닫는다. 토큰·모델·프롬프트 버전을 함께 기록한다 — 나중에 붙이면 그때까지 데이터가 없다. */
    void markDone(long meetingId, LayerName layer, LayerRun run);

    /*
     * 실패로 닫는다.
     *
     * errorCode 를 남기는 것이 중요하다. SCHEMA_INVALID·CONTEXT_EXCEEDED 는 재시도해도 토큰만
     * 태우는 영구 실패이고, 그 판정을 나중에 사람이 다시 하려면 무엇으로 실패했는지가 있어야 한다.
     *
     * @param spent 실패 전까지 실제로 쓴 토큰. **버리면 안 된다** — L3 처럼 주제마다 부르는
     *              계층은 3번째에서 터져도 앞의 2번은 이미 과금됐다. 0 으로 기록하면 QLTY-03 이
     *              실제보다 싼 기준선을 보여주고, 그 숫자로 특화 모델 전환을 판단하게 된다.
     */
    void markFailed(long meetingId, LayerName layer, String errorCode, String errorMessage, LayerRun spent);

    /* CAP-06 이 내려주는 계층 상태 목록이다. */
    List<LayerState> findStates(long meetingId);

    /* 계층 하나의 현재 상태. 없으면 아직 시작하지 않은 것이다. */
    record LayerState(LayerName layer, LayerStatus status, int tokensIn, int tokensOut) {
    }
}
