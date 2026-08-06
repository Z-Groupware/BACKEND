package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.ResumeCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionResumeResult;

/*
 * CAP-03 캡처 재개 기능을 프레젠테이션 계층에 제공하는 인바운드 Port다.
 */
@FunctionalInterface
public interface ResumeCaptureSessionUseCase {

    /* host 요청으로 PAUSED 캡처 세션을 ACTIVE 상태로 전이한다. */
    CaptureSessionResumeResult resumeCaptureSession(ResumeCaptureSessionCommand command);
}
