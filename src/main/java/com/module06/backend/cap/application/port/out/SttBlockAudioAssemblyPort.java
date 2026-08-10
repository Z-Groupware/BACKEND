package com.module06.backend.cap.application.port.out;

/*
 * 청크(opus)를 ffmpeg로 다뤄야 하는 두 작업의 경계다. 무거운 인프라(ffmpeg 바이너리·S3
 * 병렬 다운로드)가 배선되기 전까지 스텁으로 개발한다(RecordingAssemblyPort·CapObjectStoragePort와
 * 동일 패턴).
 */
public interface SttBlockAudioAssemblyPort {

    /*
     * 10분 경계(targetOffsetMs) 앞뒤 ±20초 구간의 청크들을 모아 wav(16kHz mono 16bit)로
     * 변환·이어붙여 임시 S3에 올린다. AI-01이 무음 절단 지점을 찾는 데 쓸 입력이다.
     *
     * @param availableUpToMs 지금까지 실제로 업로드된 오디오의 끝 지점(ms). 트리거는 target
     *                        지점에 도달한 그 순간(청크 40개째) 곧바로 발화하므로(명세 "40개
     *                        누적 시"), target 뒤쪽 20초는 아직 안 올라와 있을 수 있다 —
     *                        구현체는 windowEndMs를 이 값 이상으로 요청하면 안 된다(있는
     *                        데이터까지만 자른 좁은 창으로 대체한다).
     */
    ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq, long targetOffsetMs,
                                     long availableUpToMs);

    /*
     * 확정된 절단 지점(startOffsetMs~endOffsetMs)까지의 청크 전체를 이어붙여 블록 오디오
     * 하나를 만든다(stt_block.audio_s3_key). AI-01에 보낸 ±20초 윈도우보다 큰, 블록 전체 분량이다.
     * 경로는 명세대로 stt-temp/org-{orgId}/meeting-{meetingId}/blocks/{blockSeq}.wav.
     */
    String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                              long startOffsetMs, long endOffsetMs);

    record ExtractedWindow(String s3Key, long windowStartOffsetMs) {
    }
}
