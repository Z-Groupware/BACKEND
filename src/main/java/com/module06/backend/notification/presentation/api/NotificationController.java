package com.module06.backend.notification.presentation.api;

import com.module06.backend.notification.application.port.out.NotificationStreamPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "회원 개인 실시간 알림 SSE — 다른 도메인이 이 회원에게 보낸 알림을 하나의 연결로 받는다")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationStreamPort notificationStreamPort;

    public NotificationController(NotificationStreamPort notificationStreamPort) {
        this.notificationStreamPort = notificationStreamPort;
    }

    // 개인 알림 실시간 구독(SSE)
    @Operation(
            summary = "개인 알림 실시간 구독(SSE)",
            description = "로그인한 회원에게 오는 모든 실시간 알림(액션 배정, 인수인계 승인 등 — 발신 도메인은 "
                    + "NotificationPublishPort를 통해 이 채널에 얹는다)을 하나의 SSE 연결로 받는다. "
                    + "event: notification·heartbeat 두 종류를 내려준다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal(expression = "memberId") Long memberId) {
        // 수신자는 JWT principal에서 꺼낸다 — 본문/쿼리로 받으면 남의 알림을 훔쳐볼 수 있다.
        return notificationStreamPort.subscribe(memberId);
    }
}
