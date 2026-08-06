package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;

// 컨트롤러가 부르는 "명찰" — 수동 녹음 업로드(CAP-10)의 실제 구현체(ManualRecordingService)를 몰라도 되게 한다.
public interface RegisterManualRecordingUseCase {

    // 외부 녹음 파일 메타를 등록하고 단일 블록 STT를 트리거한다.
    Result registerManualRecording(RegisterManualRecordingCommand command);

    /**
     * 등록 결과.
     *
     * @param durationMs 녹음 길이(ms). ffprobe/조립 파이프라인이 async로 채우므로 등록 시점엔 0.
     * @param status     "DONE" — 수동 업로드는 제출=완료 한 동작이라 등록 즉시 완료 상태(고정 리터럴, meeting.status는 D 소유라 쓰지 않음).
     */
    record Result(
            Long meetingId,
            long durationMs,
            long sizeBytes,
            String status
    ) {
    }
}
