package com.module06.backend.cap.application.port.out;

/*
 * AI-01(Python) — 10분 경계 근처 ±20초 구간에서 무음 절단 지점을 찾는 내부 API를 부르는
 * 아웃바운드 포트다.
 *
 * <h2>넘기는 오디오는 wav(16kHz mono 16bit)여야 한다</h2>
 * 원본 청크(opus)를 그대로 보내면 안 된다 — 형식이 다르면 Python이 고쳐주지 않고 422로
 * 거절한다("오디오 원본은 한 곳에서"라는 경계를 지키기 위함). 변환은 SttBlockAudioAssemblyPort가
 * 담당한다.
 *
 * <h2>cutReason을 String으로 받는다</h2>
 * capture 도메인 소유 SttCutReason(enum)에 cap이 의존하지 않기 위함이다(CapSttBlockReferenceEntity가
 * status를 String으로 읽는 것과 같은 원칙). 값 자체는 capture로 그대로 넘어가 거기서 검증된다.
 */
public interface SttBlockCutDetectionPort {

    /**
     * @param meetingId AI-01 요청 스키마의 필수 필드(meetingId) — 회의별로 로그·모델 설정을
     *                  분리하는 데 쓰인다(실제 절단 판정 자체는 오디오만 본다)
     * @param windowAudioS3Key ±20초 윈도우 wav의 S3 키
     * @param windowStartOffsetMs 그 wav 첫 샘플의 회의 기준 경과 ms
     * @param targetOffsetMs 자르고 싶은 지점(10분 경계). 무음을 못 찾으면 여기서 자른다
     */
    CutDetectionResult detectCutPoint(long meetingId, String windowAudioS3Key, long windowStartOffsetMs,
                                      long targetOffsetMs);

    /**
     * @param silenceMs VAD_SILENCE일 때만 값이 있다(무음 길이). FALLBACK_OVERLAP이면 null이다 —
     *                  0을 채우면 "0ms 무음을 찾았다"로 읽혀 못 찾은 것과 구분이 안 된다
     *                  (AI-01 응답 스키마 주석과 같은 이유).
     */
    record CutDetectionResult(long cutOffsetMs, String cutReason, Long silenceMs) {
    }
}
