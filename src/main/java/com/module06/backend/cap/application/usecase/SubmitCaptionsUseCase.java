package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;

// 컨트롤러가 부르는 "명찰" — 실제 구현체(SubmitCaptionsService)를 몰라도 되게 해준다.
public interface SubmitCaptionsUseCase {

    // 자막 청크 배치를 저장하고 새로 저장된 조각만 브로드캐스트한다. 반환값 없음(202 Accepted).
    void submitCaptions(SubmitCaptionsCommand command);
}
