package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.PauseCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionPauseResult;

/*
 * CAP-02 캡처 일시정지 기능을 프레젠테이션 계층에 제공하는 인바운드 Port다.
 */
@FunctionalInterface
public interface PauseCaptureSessionUseCase {

    /* host 요청으로 ACTIVE 캡처 세션을 PAUSED 상태로 전이한다. */
    CaptureSessionPauseResult pauseCaptureSession(PauseCaptureSessionCommand command);
}
