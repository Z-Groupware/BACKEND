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
     * 계층을 RUNNING 으로 잠근다. 잡지 못하면 **이유를 구분해서** 돌려주고 아무것도 하지 않는다.
     *
     * 이게 중복 호출 방지의 실체다. SQS 는 at-least-once 라 같은 회의에 대한 중복 수신은
     * 언젠가 반드시 오고, 그때 두 번째 실행이 그대로 돌면 같은 회의에 토큰을 두 배로 태운다.
     * UNIQUE(meeting_id, layer) 위에서 조건부로 잡으므로 애플리케이션 락이 필요 없다.
     *
     * <h2>실행 번호를 함께 본다 (#134)</h2>
     * 잠금만으로는 **순서**가 보장되지 않는다. 잠금은 "지금 같이 돌고 있는가"만 보므로, 이미
     * 끝나서 풀린 계층을 오래된 실행이 뒤늦게 다시 잡아 옛 입력으로 덮는 것은 막지 못한다.
     * 그래서 잠그기 전에 runSeq 가 아직 이 회의의 최신인지 확인한다.
     *
     * **확인을 여기 두는 이유** — 여기가 원자적으로 검사할 수 있는 유일한 자리다. 저장 직전에
     * 따로 확인하면 확인과 저장 사이가 다시 벌어지고, 저장 경로마다(화자·요약·게이트·tuple·분배)
     * 같은 검사를 반복해야 한다. 계층의 모든 쓰기는 이 잠금 안에서 일어나므로 관문이 하나로 준다.
     *
     * @param runSeq 이 실행의 번호({@link AnalysisRunRepository#begin})
     */
    LockResult tryLock(long meetingId, LayerName layer, long runSeq);

    /*
     * 잠금 시도의 결과. 셋을 **섞지 않는 것**이 요점이다 — 셋 다 "계층을 돌리지 않는다"로
     * 끝나지만 뜻이 다르고, 합치면 사람이 원인을 못 찾는다.
     *
     *   ACQUIRED        잡았다. 계층을 돌려도 된다
     *   ALREADY_RUNNING 다른 실행이 이 계층을 잡고 있다 — 중복이 걸러진 정상 동작
     *   SUPERSEDED      더 나중에 시작한 실행이 있다. 이 실행의 결과는 이미 낡았다
     */
    enum LockResult {
        ACQUIRED, ALREADY_RUNNING, SUPERSEDED
    }

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
