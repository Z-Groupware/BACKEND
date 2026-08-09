package com.module06.backend.capture.presentation.api.response;

import java.util.List;

import com.module06.backend.capture.application.port.out.TranscriptRepository.UtteranceView;
import com.module06.backend.capture.application.usecase.GetTranscriptsUseCase.TranscriptPage;

/*
 * ANLZ-05 응답이다.
 *
 * <h2>speakerMemberId 가 null 인 것은 정상이다</h2>
 * L1 화자 귀속의 **판정 포기**다(V5.3 주석). 자막을 안 켠 참석자가 있거나 1·2등 음량 차이가
 * 3dB 미만이면 resolver 는 동전 던지기를 하지 않는다. 여기서 임의의 값으로 채우면 화자 미정인
 * 1인칭 발화("제가 할게요")가 엉뚱한 사람의 액션이 된다 — 비워 두는 편이 항상 낫다.
 *
 * 프론트에서 이 null 을 오류로 처리하면 안 된다(명세 처리 정책).
 */
public record TranscriptsResponse(
        List<UtteranceResponse> utterances,
        String nextCursor
) {

    public static TranscriptsResponse from(TranscriptPage page) {
        return new TranscriptsResponse(
                page.utterances().stream().map(UtteranceResponse::from).toList(),
                page.nextCursor());
    }

    /*
     * speakerSource 는 문자열로 내린다 — SELF_STREAM(본인 스트림 rms 최대) ·
     * ELIMINATION(참석자 2명 소거법) · null(판정 포기).
     *
     * 판정 근거를 화면까지 내리는 이유는 오귀속이 발견됐을 때 **어느 경로를 조일지** 알아야
     * 하기 때문이다. 근거를 버리면 "화자가 틀렸다"만 남는다(SpeakerSource 주석).
     */
    public record UtteranceResponse(
            Long transcriptId,
            int seq,
            Long speakerMemberId,
            String speakerSource,
            Integer startOffsetMs,
            Integer endOffsetMs,
            String content
    ) {

        static UtteranceResponse from(UtteranceView view) {
            return new UtteranceResponse(
                    view.transcriptId(),
                    view.seq(),
                    view.speakerMemberId(),
                    view.speakerSource() == null ? null : view.speakerSource().name(),
                    view.startOffsetMs(),
                    view.endOffsetMs(),
                    view.content());
        }
    }
}
