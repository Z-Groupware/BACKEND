package com.module06.backend.cap.application.usecase;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 컨트롤러가 부르는 "명찰" — 자막 실시간 구독(CAP-13)의 실제 구현체(SubscribeToCaptionsService)를 몰라도 되게 한다.
public interface SubscribeToCaptionsUseCase {

    // 열람 권한 확인 후 SSE 연결을 등록한다.
    SseEmitter subscribeToCaptions(Long meetingId, Long memberId);
}
