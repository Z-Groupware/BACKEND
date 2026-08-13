package com.module06.backend.notification.application.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.module06.backend.meeting.application.event.MeetingAttendeesAddedEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.model.NotificationType;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * MEET-09(참석자 명단 교체) 커밋 이후 새로 추가된 참석자에게만 알림을 저장하고 SSE로
 * 발행한다. MeetingCreatedNotificationTrigger와 동일한 D→소비 도메인 이벤트 연동 패턴이다.
 */
@Component
public class MeetingAttendeesAddedNotificationTrigger {

    private static final Logger log = LoggerFactory.getLogger(MeetingAttendeesAddedNotificationTrigger.class);
    private static final String TYPE_MEETING_ATTENDEE_ADDED = NotificationType.MEETING_ATTENDEE_ADDED.name();

    private final NotificationRepository notificationRepository;
    private final NotificationPublishPort notificationPublishPort;

    public MeetingAttendeesAddedNotificationTrigger(
            NotificationRepository notificationRepository,
            NotificationPublishPort notificationPublishPort
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationPublishPort = notificationPublishPort;
    }

    /* 참석자 교체 트랜잭션이 실제로 커밋된 경우에만 추가된 구성원 알림 처리를 시작한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingAttendeesAdded(MeetingAttendeesAddedEvent event) {
        String message = event.title() + " 회의 참석자 명단에 추가되었습니다.";
        for (Long memberId : event.addedAttendeeMemberIds()) {
            /* 한 회원의 실패가 나머지 신규 참석자의 알림을 막지 않도록 개별 처리한다. */
            notifyOneMember(event, memberId, message);
        }
    }

    private void notifyOneMember(MeetingAttendeesAddedEvent event, Long memberId, String message) {
        try {
            Notification notification = Notification.create(
                    event.companyId(), memberId, TYPE_MEETING_ATTENDEE_ADDED, event.meetingId(), message);

            if (!notificationRepository.saveIfAbsent(notification)) {
                log.info("이미 보낸 참석자 추가 알림 — 스킵. meetingId={} memberId={}",
                        event.meetingId(), memberId);
                return;
            }

            NotificationEvent notificationEvent = new NotificationEvent(
                    TYPE_MEETING_ATTENDEE_ADDED,
                    new MeetingAttendeeAddedPayload(event.meetingId(), event.title(), message,
                            event.startAt(), event.endAt())
            );
            notificationPublishPort.publish(memberId, notificationEvent);
        } catch (RuntimeException exception) {
            /* 커밋 이후 실패는 던져봐야 갈 곳이 없다 — 로그로 남기고 나머지 회원을 계속 처리한다. */
            log.error("참석자 추가 알림 처리 실패 — meetingId={} memberId={}",
                    event.meetingId(), memberId, exception);
        }
    }

    /** 프론트의 MEETING_ATTENDEE_ADDED 분기에 전달하는 JSON 직렬화용 불변 payload다. */
    public record MeetingAttendeeAddedPayload(
            Long meetingId,
            String title,
            String message,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }
}
