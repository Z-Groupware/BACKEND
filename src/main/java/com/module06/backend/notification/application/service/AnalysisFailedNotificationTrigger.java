package com.module06.backend.notification.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.module06.backend.capture.application.event.AnalysisFailedEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.model.NotificationType;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * A(분석) 도메인이 회의 종료 후 백그라운드 요약에 실패한 뒤 발행한 AnalysisFailedEvent를
 * 받아 개설자에게 알림을 저장하고 실시간으로 보낸다.
 *
 * @EventListener를 쓰는 이유는 AnalysisCompletedNotificationTrigger와 같다 — 발행 시점에
 * 대기 중인 트랜잭션이 없다(MeetingCompletedAnalysisTrigger의 비동기 스레드에서 발행).
 */
@Component
public class AnalysisFailedNotificationTrigger {

    private static final Logger log = LoggerFactory.getLogger(AnalysisFailedNotificationTrigger.class);
    private static final String TYPE_ANALYSIS_FAILED = NotificationType.ANALYSIS_FAILED.name();

    private final NotificationRepository notificationRepository;
    private final NotificationPublishPort notificationPublishPort;

    public AnalysisFailedNotificationTrigger(
            NotificationRepository notificationRepository,
            NotificationPublishPort notificationPublishPort
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationPublishPort = notificationPublishPort;
    }

    @EventListener
    public void onAnalysisFailed(AnalysisFailedEvent event) {
        String message = event.title() + " 회의 요약에 실패했습니다.";
        try {
            Notification notification = Notification.create(
                    event.companyId(), event.hostMemberId(), TYPE_ANALYSIS_FAILED, event.meetingId(), message);

            if (!notificationRepository.saveIfAbsent(notification)) {
                log.info("이미 보낸 분석 실패 알림 — 스킵. meetingId={} memberId={}",
                        event.meetingId(), event.hostMemberId());
                return;
            }

            NotificationEvent notificationEvent = new NotificationEvent(
                    TYPE_ANALYSIS_FAILED,
                    new AnalysisFailedPayload(event.meetingId(), event.title(), message, event.errorCode())
            );
            notificationPublishPort.publish(event.hostMemberId(), notificationEvent);
        } catch (RuntimeException exception) {
            // 던져봐야 갈 곳이 없다 — 분석은 이미 끝났고 이 실패로 되돌릴 것이 없다.
            log.error("분석 실패 알림 처리 실패 — meetingId={} memberId={}",
                    event.meetingId(), event.hostMemberId(), exception);
        }
    }

    /** 프론트의 ANALYSIS_FAILED 분기에 전달하는 JSON 직렬화용 불변 payload다. */
    public record AnalysisFailedPayload(Long meetingId, String title, String message, String errorCode) {
    }
}
