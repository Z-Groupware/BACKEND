package com.module06.backend.capture.application.result;

import java.util.List;

import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

/*
 * CAP-06 응답의 재료다.
 *
 * 명세의 blocks·gaps·estimatedRemainingSec 는 이 슬라이스에 아직 없다 — STT 블록과
 * stt_gap 을 채우는 쪽(조립·Transcribe)이 붙지 않았다. **빈 값으로 채워 내려주지 않는다.**
 * gaps 를 빈 배열로 내려주면 화면이 "구멍 없음"으로 읽고 배너를 띄우지 않는데, 실제로는
 * 아직 아무도 확인하지 않은 상태다. 구멍을 숨기면 담당자가 누락을 모른 채 분배하고
 * 그 액션은 영구히 사라진다(V5.5 주석).
 */
public record ProcessingStatus(
        OverallStatus status,
        List<LayerProgress> layers
) {

    public enum OverallStatus {
        /* 계층 기록이 하나도 없다 — 분석을 시작한 적이 없다. */
        NOT_STARTED,
        RUNNING,
        DONE,
        FAILED
    }

    public record LayerProgress(LayerName layer, LayerStatus status, int tokensIn, int tokensOut) {
    }

    /*
     * 계층 상태를 회의 단위 상태로 접는다.
     *
     * 우선순위가 있다: 하나라도 실패했으면 FAILED, 아니면 돌고 있으면 RUNNING, 그다음 DONE.
     * 실패를 RUNNING 뒤로 미루면 멈춘 잡이 "아직 도는 중"으로 보여 아무도 재개하지 않는다.
     */
    public static ProcessingStatus of(List<LayerProgress> layers) {
        if (layers.isEmpty()) {
            return new ProcessingStatus(OverallStatus.NOT_STARTED, List.of());
        }
        if (layers.stream().anyMatch(l -> l.status() == LayerStatus.FAILED)) {
            return new ProcessingStatus(OverallStatus.FAILED, layers);
        }
        if (layers.stream().anyMatch(l -> l.status() == LayerStatus.RUNNING
                || l.status() == LayerStatus.PENDING)) {
            return new ProcessingStatus(OverallStatus.RUNNING, layers);
        }
        return new ProcessingStatus(OverallStatus.DONE, layers);
    }
}
