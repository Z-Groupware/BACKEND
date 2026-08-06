package com.module06.backend.capture.domain.model;

import java.math.BigDecimal;

/*
 * 참석자 브라우저가 회의 중에 보낸 자막 한 조각이다(caption_chunk · CAP-11 수신).
 *
 * 정본(transcript_chunk)과 성격이 정반대다.
 *   정본 = 녹음 원본을 STT 로 받아쓴 것 — 사후, 정확, **화자를 모른다**
 *   자막 = 참석자 브라우저가 보낸 것 — 실시간, 부정확, **화자는 확실하다**
 * L1 이 하는 일은 이 둘을 시간창으로 맞춰 정본에 화자를 이식하는 것이다.
 *
 * rms 는 그 구간의 마이크 음량(dBFS, 음수)이다. **화자 판정의 유일한 근거**이고
 * NOT NULL 이다 — 없으면 판정 자체가 성립하지 않는다. 브라우저 AnalyserNode 로 계산한
 * 값이라 모델이 아니라 산수다.
 *
 * BigDecimal 을 쓰는 이유: 컬럼이 DECIMAL(6,2) 다. double 로 받으면 3dB 임계값 비교가
 * 부동소수 오차에 걸리는 경계 입력이 생긴다 — 판정 포기와 확정을 가르는 값이라 그 경계가
 * 흔들리면 안 된다.
 */
public record CaptionChunk(
        Long memberId,
        Integer startOffsetMs,
        Integer endOffsetMs,
        BigDecimal rms
) {
}
