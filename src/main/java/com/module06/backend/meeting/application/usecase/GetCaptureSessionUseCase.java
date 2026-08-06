package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetCaptureSessionQuery;
import com.module06.backend.meeting.application.result.CaptureSessionStateResult;

/*
 * CAP-10 현재 캡처 세션 조회 기능을 프레젠테이션 계층에 제공하는 인바운드 Port다.
 */
@FunctionalInterface
public interface GetCaptureSessionUseCase {

    /* 예약 참석자에게 현재 캡처 세션의 D 소유 상태와 시간축을 반환한다. */
    CaptureSessionStateResult getCaptureSession(GetCaptureSessionQuery query);
}
