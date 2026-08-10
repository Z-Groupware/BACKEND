package com.module06.backend.cap.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.SttBlockCutDetectionPort;

/* comment.
    SttBlockCutDetectionPort의 스텁 구현 — AI-01 실제 엔드포인트 경로·JSON 계약이 확정되기 전까지
    쓴다(RecordingAssemblyStubAdapter와 동일 패턴). 무음을 못 찾은 것으로 간주해 항상 targetOffsetMs
    그 자리에서 자른다(FALLBACK_OVERLAP) — 실제 호출이 안 붙어도 파이프라인 전체(윈도우 추출 →
    절단 지점 결정 → 블록 조립 → stt_block 생성)가 끝까지 도는지는 이 스텁만으로 검증 가능하다.
*/
@Component
public class SttBlockCutDetectionStubAdapter implements SttBlockCutDetectionPort {

    private static final Logger log = LoggerFactory.getLogger(SttBlockCutDetectionStubAdapter.class);

    @Override
    public CutDetectionResult detectCutPoint(String windowAudioS3Key, long windowStartOffsetMs,
                                             long targetOffsetMs) {
        log.info("AI-01 절단 지점 탐지(stub) — windowAudioS3Key={}, windowStartOffsetMs={}, targetOffsetMs={}. "
                + "실 엔드포인트 연결 전까지 targetOffsetMs 그대로 자른다(FALLBACK_OVERLAP).",
                windowAudioS3Key, windowStartOffsetMs, targetOffsetMs);
        return new CutDetectionResult(targetOffsetMs, "FALLBACK_OVERLAP", 0L);
    }
}
