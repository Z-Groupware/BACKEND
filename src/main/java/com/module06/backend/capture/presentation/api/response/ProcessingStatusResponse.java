package com.module06.backend.capture.presentation.api.response;

import java.util.List;

import com.module06.backend.capture.application.result.ProcessingStatus;

/*
 * CAP-06 응답이다.
 *
 * <h2>gaps 를 빈 배열로 내려주되 gapsChecked 를 함께 준다</h2>
 * 이 필드는 한 번 방향이 뒤집힌 자리라 근거를 남긴다.
 *
 * 처음에는 필드를 아예 넣지 않았다 — 빈 배열을 주면 화면이 "구멍 없음"으로 읽고 배너를 띄우지
 * 않는데, STT 블록·stt_gap 을 채우는 쪽(조립·Transcribe)이 아직 없어서 그건 **확인하지 않은
 * 것을 확인된 것처럼 보여주는 것**이기 때문이다.
 *
 * 그런데 필드를 빼는 것도 나름의 사고를 만든다(CodeRabbit PR #84 지적) — 계약상 필수 배열이라
 * 프론트가 {@code data.gaps.length} 로 읽고 그 자리에서 터진다. 명세를 지키지 않는 응답이다.
 *
 * 그래서 **둘 다 준다.** {@code gaps} 는 계약대로 항상 존재하고(지금은 빈 배열),
 * {@code gapsChecked} 가 "그 빈 배열이 확인 결과인가"를 말한다. 배너 판단은 이 값을 봐야 한다.
 *   · {@code gapsChecked=false} — 아직 확인하지 않았다. "구멍 없음"이 아니다
 *   · {@code gapsChecked=true}  — 실제로 확인했고 gaps 가 그 결과다
 * stt_gap 을 채우는 경로가 붙으면 이 값이 true 가 되고 gaps 에 실제 구간이 담긴다.
 *
 * 명세의 blocks · estimatedRemainingSec 는 여전히 넣지 않는다. 둘 다 STT 블록 관리가 있어야
 * 만들 수 있는 값이고, 지어낼 방법이 없다.
 */
public record ProcessingStatusResponse(
        String status,
        List<LayerResponse> layers,
        List<GapResponse> gaps,
        boolean gapsChecked,
        BlocksResponse blocks,
        Integer estimatedRemainingSec
) {

    public static ProcessingStatusResponse from(ProcessingStatus status) {
        return new ProcessingStatusResponse(
                status.status().name(),
                status.layers().stream()
                        .map(layer -> new LayerResponse(
                                layer.layer().wireValue(),
                                layer.status().name(),
                                layer.tokensIn(),
                                layer.tokensOut(),
                                layer.stalled()))
                        .toList(),
                /*
                 * 이제 실제 값이 온다. 폴링이 블록을 FAILED 로 닫을 때 그 구간을 stt_gap 에
                 * 남기고, 재처리가 성공하면 지운다 — 예전 주석의 "채우는 경로가 아직 없다"가
                 * 해소된 자리다.
                 */
                status.gaps().stream()
                        .map(gap -> new GapResponse(gap.startMs(), gap.endMs(), gap.reason(),
                                gap.mentionedNames(), gap.keywords()))
                        .toList(),
                status.gapsChecked(),
                new BlocksResponse(status.blocks().total(), status.blocks().done(),
                        status.blocks().failed()),
                /*
                 * null 로 내려갈 수 있다. **0 으로 바꾸지 않는다** — 0 은 "곧 끝난다"로 읽히고,
                 * null 은 "아직 못 잰다"다. 이 회의에서 끝난 블록이 없으면 곱할 처리율이 없고,
                 * 받아쓰기가 끝난 뒤의 계층 소요는 주제·tuple 수에 좌우돼 앞 계층으로 못 잰다
                 * (SttProgress 주석). 화면은 null 이면 남은 시간을 감춘다.
                 */
                status.blocks().estimatedRemainingSec());
    }

    /*
     * layer 는 "L1.5"·"L3.5" 같은 전송 값이다. enum 이름(L1_5)이 아니다.
     *
     * @param stalled 이 계층을 잡은 실행이 끊겼다(#177 · 배포·크래시). status 는 여전히
     *                RUNNING 이지만 실제로 도는 것은 없다 — 화면은 이 값을 보고 "중단됨 ·
     *                다시 분석"으로 안내해야 한다. status 만 보면 「AI 처리 중」이 영원히 뜬다.
     *                전체 status 는 이 경우 FAILED 로 내려간다.
     */
    public record LayerResponse(String layer, String status, int tokensIn, int tokensOut,
                                boolean stalled) {
    }

    /*
     * 받아쓰기 블록 집계(명세 CAP-06 의 {@code blocks}).
     *
     * <h2>목록이 아니라 집계다 — STT-03 과 겹치지 않는다</h2>
     * 블록 하나하나의 상태·사유·재시도 횟수는 STT-03 이 준다. 여기서 같은 목록을 또 주면 두
     * 응답이 같은 사실을 각자 말하게 되고, 화면이 어느 쪽을 믿을지 정해야 한다. 이 응답이
     * 답하는 질문은 "얼마나 남았나"이므로 세 숫자로 충분하다.
     *
     * total 에서 done·failed 를 빼면 아직 도는 블록 수다. 그 값을 따로 주지 않는 이유 —
     * 세 숫자가 서로 어긋날 자리를 만들지 않는다.
     */
    public record BlocksResponse(int total, int done, int failed) {
    }

    /*
     * STT 구멍 한 구간. 아직 채우지 않지만 모양을 먼저 고정한다 — 프론트가 배너·재생 동선을
     * 만들 때 필드가 나중에 바뀌면 그 화면을 다시 짜야 한다.
     *
     * mentionedNames·keywords 를 함께 주는 이유: 구간만 알려주면 담당자가 10분을 다시 듣는다.
     * "20:00~30:00, 김민섭 언급, 광고·섭외 키워드"까지 좁혀주면 30초만 확인한다(V5.5 주석).
     */
    public record GapResponse(
            int startMs,
            int endMs,
            String reason,
            List<String> mentionedNames,
            List<String> keywords
    ) {
    }
}
