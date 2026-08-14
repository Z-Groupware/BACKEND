package com.module06.backend.capture.application.result;

import java.util.List;

import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.domain.model.SttProgress;

/*
 * CAP-06 응답의 재료다.
 *
 * <h2>gaps 가 실제 값으로 채워진다</h2>
 * 예전 주석이 "채우는 쪽이 붙지 않았다"고 적어 둔 자리다. 이제 폴링이 블록을 FAILED 로 닫을 때
 * 그 구간을 stt_gap 에 남기므로, 이 목록에 실제 구간이 담긴다.
 *
 * 빈 배열이라고 "구멍 없음"이 아니다 — 그 판단은 gapsChecked 가 한다. 구멍을 숨기면 담당자가
 * 누락을 모른 채 분배하고 그 액션은 영구히 사라진다(V5.5 주석).
 *
 * 명세의 blocks·estimatedRemainingSec 는 여전히 넣지 않는다. 블록 목록은 STT-03 이 이미
 * 주는 값이고, 남은 시간은 제공자가 알려주지 않아 지어낼 방법이 없다.
 */
public record ProcessingStatus(
        OverallStatus status,
        List<LayerProgress> layers,
        List<Gap> gaps,
        boolean gapsChecked,
        SttProgress blocks
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
        return of(layers, List.of(), false, SttProgress.of(List.of(), null));
    }

    /*
     * 구멍과 블록 진행도까지 함께 담는다(CAP-06).
     *
     * @param gaps        받아쓰기 구멍. 비어 있다는 것이 곧 "구멍 없음"은 아니다 — 그 판단은
     *                    gapsChecked 가 한다
     * @param gapsChecked 그 빈 배열이 **확인 결과인가.** false 면 아직 확인하지 않은 것이다
     * @param blocks      받아쓰기 진행도와 남은 시간. 남은 시간은 못 재면 null 이다 —
     *                    지어내지 않는다(SttProgress 주석)
     */
    public static ProcessingStatus of(List<LayerProgress> layers, List<Gap> gaps, boolean gapsChecked,
                                      SttProgress blocks) {
        if (blocks != null && blocks.failed() > 0) {
            return new ProcessingStatus(OverallStatus.FAILED, layers, gaps, gapsChecked, blocks);
        }
        if (layers.isEmpty()) {
            return new ProcessingStatus(OverallStatus.NOT_STARTED, layers, gaps, gapsChecked, blocks);
        }
        if (layers.stream().anyMatch(l -> l.status() == LayerStatus.FAILED || l.stalled())) {
            return new ProcessingStatus(OverallStatus.FAILED, layers, gaps, gapsChecked, blocks);
        }
        if (layers.stream().anyMatch(LayerProgress::live)) {
            return new ProcessingStatus(OverallStatus.RUNNING, layers, gaps, gapsChecked, blocks);
        }
        return new ProcessingStatus(OverallStatus.DONE, layers, gaps, gapsChecked, blocks);
    }

    /*
     * 구멍 한 구간.
     *
     * mentionedNames·keywords 는 그 구간 자막 **본문**에서 뽑는 값이고, 우리 자막 읽기 포트가
     * 본문을 주지 않아 지금은 항상 비어 있다(SttGapRepository.GapView 주석). 지어내지 않는다.
     */
    public record Gap(
            int startMs,
            int endMs,
            String reason,
            List<String> mentionedNames,
            List<String> keywords
    ) {
    }
}
