package com.module06.backend.cap.infrastructure.assembly;

// requireSufficientDiskSpace 전용 예외 — RecordingAssemblyS3FfmpegAdapter.startAssembly의 다른
// 실패(ffmpeg 오류, 회의 없음 등)와 구분하기 위해서다. 일반 IllegalStateException으로 두면
// startAssembly의 바깥 catch(RuntimeException)가 모든 실패를 똑같이 취급해, 디스크 부족만 골라서
// 재시도 등록(PendingAssemblyRetryRegistry)하는 분기를 만들 수 없다.
class InsufficientDiskSpaceException extends RuntimeException {

    InsufficientDiskSpaceException(String message) {
        super(message);
    }
}
