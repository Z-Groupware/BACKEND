package com.module06.backend.cap.application.port.out;

/* comment.
    CAP과 STT 파이프라인 사이의 경계. 수동 업로드(CAP-10)된 통합 녹음본을 "단일 블록"으로 STT 돌리도록
    트리거만 한다(라이브 경로는 10분 블록마다 STT가 도는데, 수동 업로드는 그 트리거가 없어 여기서 챙긴다).
    실제 stt_block 생성 + AWS Transcribe 호출은 이태연 STT 도메인 인프라라, 실 어댑터 전까지 스텁으로 개발한다
    (RecordingAssemblyPort와 동일 패턴). duration 복구도 이 파이프라인이 async로 채운다.
*/
public interface MeetingRecordingSttPort {

    /** 수동 업로드본 전체를 단일 블록으로 STT 트리거한다(best-effort). */
    void triggerWholeFileStt(Long meetingId, String s3Key);
}
