package com.module06.backend.cap.application.port.out;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/* comment.
    CAP-13(자막 실시간 구독 SSE) 연결 등록·해제를 담당하는 인프라 경계. 실제 emitter 레지스트리·Redis
    pub/sub 배선은 인프라 계층(CaptionStreamRegistry)이 갖고, 서비스는 인가만 검증하고 여기에 위임한다.
*/
public interface CaptionStreamPort {

    /** 이 회의·이 참석자의 SSE 연결을 새로 만들어 등록하고 emitter를 반환한다. */
    SseEmitter subscribe(Long meetingId, Long memberId);
}
