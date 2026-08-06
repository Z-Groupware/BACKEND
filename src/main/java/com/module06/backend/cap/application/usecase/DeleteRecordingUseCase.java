package com.module06.backend.cap.application.usecase;

import java.time.LocalDateTime;

// 컨트롤러가 부르는 "명찰" — 녹음 삭제(CAP-15)의 실제 구현체(DeleteRecordingService)를 몰라도 되게 한다.
public interface DeleteRecordingUseCase {

    // 열람/삭제 권한(owner·admin + 같은 회사) 확인 후 오디오(recording·recording_part·S3)를 하드 삭제한다.
    // confirm=true면 STT/분석 미완료 차단을 강행한다.
    Result deleteRecording(Long meetingId, Long companyId, boolean confirm);

    /**
     * 삭제 결과.
     *
     * @param deletedAt  삭제 시각.
     * @param freedBytes 삭제로 회수된 용량(byte) — 지운 녹음본의 file_size. 저장 용량 집계(recording.file_size SUM)가 그만큼 줄어든다.
     */
    record Result(
            LocalDateTime deletedAt,
            long freedBytes
    ) {
    }
}
