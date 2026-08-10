package com.module06.backend.cap.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.SttBlockCutDetectionPort;

/* comment.
    SttBlockCutDetectionPort의 스텁 구현 — 로컬/테스트 전용(@Profile("!prod")). 운영은
    SttBlockCutDetectionHttpAdapter(AI-01 실 호출, PR #15 머지됨)가 대체한다 —
    CapObjectStorageStubAdapter/CapS3ObjectStorageAdapter와 동일한 stub↔실 어댑터 분리 패턴.
    무음을 못 찾은 것으로 간주해 항상 targetOffsetMs 그 자리에서 자른다(FALLBACK_OVERLAP) —
    로컬에서도 파이프라인 전체(윈도우 추출 → 절단 지점 결정 → 블록 조립 → stt_block 생성)가
    끝까지 도는지는 이 스텁만으로 검증 가능하다.
*/
@Component
@Profile("!prod")
public class SttBlockCutDetectionStubAdapter implements SttBlockCutDetectionPort {

    private static final Logger log = LoggerFactory.getLogger(SttBlockCutDetectionStubAdapter.class);

    @Override
    public CutDetectionResult detectCutPoint(long meetingId, String windowAudioS3Key, long windowStartOffsetMs,
                                             long targetOffsetMs) {
        log.info("AI-01 절단 지점 탐지(stub) — meetingId={}, windowAudioS3Key={}, windowStartOffsetMs={}, "
                + "targetOffsetMs={}. 로컬/테스트 전용, targetOffsetMs 그대로 자른다(FALLBACK_OVERLAP).",
                meetingId, windowAudioS3Key, windowStartOffsetMs, targetOffsetMs);
        // FALLBACK_OVERLAP은 무음 길이가 없다 — 0이 아니라 null(AI-01 응답 계약과 동일 규칙).
        return new CutDetectionResult(targetOffsetMs, "FALLBACK_OVERLAP", null);
    }
}
