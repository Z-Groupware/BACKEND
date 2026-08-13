package com.module06.backend.notification.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.module06.backend.capture.application.event.AnalysisCompletedEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.model.NotificationType;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * A(분석) 도메인이 회의 종료 후 백그라운드 요약을 마친 뒤 발행한 AnalysisCompletedEvent를
 * 받아 개설자에게 알림을 저장하고 실시간으로 보낸다.
 *
 * <h2>왜 @TransactionalEventListener가 아니라 @EventListener인가</h2>
 * MeetingCanceledNotificationTrigger 등 D 이벤트는 원본 트랜잭션 커밋 직후 같은 스레드에서
 * 소비되므로 AFTER_COMMIT을 기다려야 한다. 이 이벤트는 다르다 — MeetingCompletedAnalysisTrigger가
 * runAnalysisUseCase.run()의 트랜잭션이 이미 끝난 뒤(@Async 스레드) 발행한다. 대기할 트랜잭션
 * 자체가 없는 시점이라 @TransactionalEventListener(AFTER_COMMIT)를 붙이면 "활성 트랜잭션 없음"으로
 * 조용히 건너뛰어져 알림이 영원히 안 나간다.
 *
 * 나머지 원칙(먼저 저장 후 발행, meeting/member 재조회 안 함, 회사 섞임 불가, 던지지 않음)은
 * MeetingCanceledNotificationTrigger와 동일하다.
 */
@Component
public class AnalysisCompletedNotificationTrigger {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCompletedNotificationTrigger.class);
    private static final String TYPE_ANALYSIS_COMPLETED = NotificationType.ANALYSIS_COMPLETED.name();

    private final NotificationRepository notificationRepository;
    private final NotificationPublishPort notificationPublishPort;

    public AnalysisCompletedNotificationTrigger(
            NotificationRepository notificationRepository,
            NotificationPublishPort notificationPublishPort
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationPublishPort = notificationPublishPort;
    }

    @EventListener
    public void onAnalysisCompleted(AnalysisCompletedEvent event) {
        String message = event.title() + " 회의 요약이 완료되었습니다.";
        try {
            Notification notification = Notification.create(
                    event.companyId(), event.hostMemberId(), TYPE_ANALYSIS_COMPLETED, event.meetingId(), message);

            if (!notificationRepository.saveIfAbsent(notification)) {
                log.info("이미 보낸 분석 완료 알림 — 스킵. meetingId={} memberId={}",
                        event.meetingId(), event.hostMemberId());
                return;
            }

            NotificationEvent notificationEvent = new NotificationEvent(
                    TYPE_ANALYSIS_COMPLETED,
                    new AnalysisCompletedPayload(event.meetingId(), event.title(), message, event.topicCount())
            );
            notificationPublishPort.publish(event.hostMemberId(), notificationEvent);
        } catch (RuntimeException exception) {
            // 던져봐야 갈 곳이 없다 — 분석은 이미 끝났고 이 실패로 되돌릴 것이 없다.
            log.error("분석 완료 알림 처리 실패 — meetingId={} memberId={}",
                    event.meetingId(), event.hostMemberId(), exception);
        }
    }

    /** 프론트의 ANALYSIS_COMPLETED 분기에 전달하는 JSON 직렬화용 불변 payload다. */
    public record AnalysisCompletedPayload(Long meetingId, String title, String message, int topicCount) {
    }
}
