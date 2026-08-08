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

    /*
     * @param stalled RUNNING 인데 그 실행이 이미 죽은 계층이다(#177). 배포·크래시로 끊긴
     *                자리이고, status 는 여전히 RUNNING 이다 — 저장된 값과 그 값이 아직
     *                유효한지를 따로 준다.
     */
    public record LayerProgress(LayerName layer, LayerStatus status, int tokensIn, int tokensOut,
                                boolean stalled) {

        /* 실제로 지금 돌고 있는 계층인가. 멈춘 RUNNING 은 여기에 들지 않는다. */
        boolean live() {
            return !stalled && (status == LayerStatus.RUNNING || status == LayerStatus.PENDING);
        }
    }

    /*
     * 계층 상태를 회의 단위 상태로 접는다.
     *
     * 우선순위가 있다: 하나라도 실패했으면 FAILED, 아니면 돌고 있으면 RUNNING, 그다음 DONE.
     * 실패를 RUNNING 뒤로 미루면 멈춘 잡이 "아직 도는 중"으로 보여 아무도 재개하지 않는다.
     *
     * <h2>멈춘 RUNNING 은 FAILED 로 접는다 (#177)</h2>
     * 실행이 죽어 남은 RUNNING 을 RUNNING 으로 접으면 두 가지가 함께 망가진다 —
     * 화면에는 「AI 처리 중」이 끝나지 않고, ANLZ-01 이 그 상태를 보고 409(ANALYSIS_ALREADY_RUNNING)
     * 를 주어 **사람이 다시 돌릴 수도 없다.** 잠금은 회수되는데 유스케이스가 막으면 고친 것이
     * 아니다. 멈춘 것은 실패로 접어야 재개(ANLZ-02)와 재실행이 둘 다 열린다.
     */
    public static ProcessingStatus of(List<LayerProgress> layers) {
        if (layers.isEmpty()) {
            return new ProcessingStatus(OverallStatus.NOT_STARTED, List.of());
        }
        if (layers.stream().anyMatch(l -> l.status() == LayerStatus.FAILED || l.stalled())) {
            return new ProcessingStatus(OverallStatus.FAILED, layers);
        }
        if (layers.stream().anyMatch(LayerProgress::live)) {
            return new ProcessingStatus(OverallStatus.RUNNING, layers);
        }
        return new ProcessingStatus(OverallStatus.DONE, layers);
    }
}
