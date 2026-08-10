package com.module06.backend.cap.infrastructure.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.SttBlockAudioAssemblyPort;

/* comment.
    SttBlockAudioAssemblyPort의 스텁 구현 — 실제 ffmpeg 실행(청크 다운로드·opus→wav 변환·이어붙이기)
    인프라가 배선되기 전까지 트리거를 로그로만 남긴다(RecordingAssemblyStubAdapter와 동일 패턴).
    운영 배포 이미지에 ffmpeg가 아직 설치되지 않았다 — 실 어댑터로 교체하기 전 Dockerfile에도
    설치가 필요하다(별도 확인 필요, 이 스텁과 무관하게 진행 가능).
*/
@Component
public class SttBlockAudioAssemblyStubAdapter implements SttBlockAudioAssemblyPort {

    private static final Logger log = LoggerFactory.getLogger(SttBlockAudioAssemblyStubAdapter.class);

    @Override
    public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq, long targetOffsetMs) {
        String s3Key = "stt-temp/org-%d/meeting-%d/segments/%d/cut-window-%d.wav"
                .formatted(companyId, meetingId, segmentSeq, targetOffsetMs);
        long windowStartOffsetMs = Math.max(0L, targetOffsetMs - 20_000L);
        log.info("절단 지점 탐지용 ±20초 윈도우 추출(stub) — meetingId={}, segmentSeq={}, targetOffsetMs={}. "
                + "실제 ffmpeg 변환·업로드는 후속 인프라에서 수행.", meetingId, segmentSeq, targetOffsetMs);
        return new ExtractedWindow(s3Key, windowStartOffsetMs);
    }

    @Override
    public String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                                     long startOffsetMs, long endOffsetMs) {
        // 명세 경로 그대로: stt-temp/org-{orgId}/meeting-{meetingId}/blocks/{blockSeq}.wav
        String s3Key = "stt-temp/org-%d/meeting-%d/blocks/%d.wav".formatted(companyId, meetingId, blockSeq);
        log.info("블록 오디오 조립(stub) — meetingId={}, blockSeq={}, {}~{}ms. "
                + "실제 ffmpeg 이어붙이기·업로드는 후속 인프라에서 수행.",
                meetingId, blockSeq, startOffsetMs, endOffsetMs);
        return s3Key;
    }
}
