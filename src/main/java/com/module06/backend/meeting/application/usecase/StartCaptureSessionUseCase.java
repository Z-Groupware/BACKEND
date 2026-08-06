package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;

/*
 * CAP-01 캡처 세션 시작 기능을 프레젠테이션 계층에 제공하는 인바운드 Port다.
 */
@FunctionalInterface
public interface StartCaptureSessionUseCase {

    /* 진행 중인 회의에 단 하나의 ACTIVE 캡처 세션을 생성한다. */
    CaptureSessionStartResult startCaptureSession(StartCaptureSessionCommand command);
}
