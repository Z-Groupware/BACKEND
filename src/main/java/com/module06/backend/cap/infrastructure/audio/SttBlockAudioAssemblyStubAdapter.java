package com.module06.backend.cap.infrastructure.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.SttBlockAudioAssemblyPort;

/* comment.
    SttBlockAudioAssemblyPort의 스텁 구현 — 로컬/테스트 전용(@Profile("!prod")). 운영은
    SttBlockAudioAssemblyS3FfmpegAdapter(실제 ffmpeg 실행)가 대체한다 — CapObjectStorageStubAdapter/
    CapS3ObjectStorageAdapter와 동일한 stub↔실 어댑터 분리 패턴.
*/
@Component
@Profile("!prod")
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
