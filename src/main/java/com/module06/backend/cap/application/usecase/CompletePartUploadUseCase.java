package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.CompletePartUploadCommand;

// 컨트롤러가 부르는 "명찰" — 실제 구현체(CaptureUploadService)를 몰라도 되게 해준다.
public interface CompletePartUploadUseCase {

    // 청크 업로드 완료를 기록한다 (저장 + 하트비트 갱신). 반환값 없음(202 Accepted).
    void completePartUpload(CompletePartUploadCommand command);
}
