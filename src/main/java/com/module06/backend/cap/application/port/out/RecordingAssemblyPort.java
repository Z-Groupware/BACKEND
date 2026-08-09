package com.module06.backend.cap.application.port.out;

/* comment.
    CAP과 조립 파이프라인 사이의 경계. seq 연속성 검증을 통과한 녹음의 조립을 트리거만 한다.
    실제 조립(세그먼트별 병렬 다운로드 → ffmpeg 결합 → duration 복구 → recording.ogg 등록 →
    parts/ 조각 삭제)은 무거운 인프라라 실 어댑터가 나오기 전까지 스텁으로 개발한다
    (CapObjectStoragePort와 동일 패턴). 조립 완료 후 상태 전이는 이 파이프라인이 책임진다.
*/
public interface RecordingAssemblyPort {

    /** 녹음 조립 파이프라인을 트리거한다(best-effort). lastSegmentSeq/lastSeq는 조립 범위 기준. */
    void startAssembly(Long meetingId, int lastSegmentSeq, int lastSeq);
}
