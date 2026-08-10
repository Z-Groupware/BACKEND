package com.module06.backend.notification.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.model.NotificationType;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * 회의 예약 커밋 이후 같은 회사의 구성원 전체에게 회의 개설 알림을 저장하고 SSE로 발행한다.
 * 참석자 명단이 아니라 회사 구성원 조회 결과를 사용하므로 사내 공용 회의 개설 소식을 모두 받을 수 있다.
 */
@Component
public class MeetingCreatedNotificationTrigger {

    /* 커밋 이후 처리 실패를 원본 회의 트랜잭션과 분리해 기록하는 로거다. */
    private static final Logger log = LoggerFactory.getLogger(MeetingCreatedNotificationTrigger.class);

    /* DB ENUM과 프론트 SSE 분기에서 함께 사용하는 회의 개설 알림 타입이다. */
    private static final String TYPE_MEETING_CREATED = NotificationType.MEETING_CREATED.name();

    /* 동일 회사에서 알림을 받을 비삭제 구성원 식별자를 조회하는 Port다. */
    private final CompanyMemberQueryPort companyMemberQueryPort;

    /* 회원별 알림을 중복 방지 제약과 함께 저장하는 저장소다. */
    private final NotificationRepository notificationRepository;

    /* 저장에 성공한 알림을 개인 SSE 채널로 발행하는 Port다. */
    private final NotificationPublishPort notificationPublishPort;

    /* 필요한 조회·저장·발행 의존성을 명시적으로 주입한다. */
    public MeetingCreatedNotificationTrigger(
            CompanyMemberQueryPort companyMemberQueryPort,
            NotificationRepository notificationRepository,
            NotificationPublishPort notificationPublishPort
    ) {
        /* 각 협력자를 필드에 보관해 커밋 이후 알림 처리에 사용한다. */
        this.companyMemberQueryPort = companyMemberQueryPort;
        this.notificationRepository = notificationRepository;
        this.notificationPublishPort = notificationPublishPort;
    }

    /* 회의 생성 트랜잭션이 실제로 커밋된 경우에만 회사 구성원 알림 처리를 시작한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingCreated(MeetingReservedEvent event) {
        /* AFTER_COMMIT 예외는 원본을 되돌릴 수 없으므로 조회 실패도 로그로 남기고 종료한다. */
        List<Long> recipientMemberIds;
        try {
            /* 이벤트의 검증된 회사 식별자로 수신자 범위를 한 회사 안에 고정한다. */
            recipientMemberIds = companyMemberQueryPort.findActiveMemberIds(event.companyId());
        } catch (RuntimeException exception) {
            /* 구성원 조회 실패가 이벤트 처리 스레드 밖으로 전파되지 않게 한다. */
            log.error("회의 개설 알림 수신자 조회 실패 — meetingId={} companyId={}",
                    event.meetingId(), event.companyId(), exception);
            return;
        }

        /* 제목을 포함한 공통 메시지를 한 번 만들고 회원별 저장·발행에 재사용한다. */
        String message = event.title() + " 회의가 개설되었습니다.";
        for (Long memberId : recipientMemberIds) {
            /* 한 회원의 실패가 같은 회사의 다른 회원 알림을 막지 않도록 개별 처리한다. */
            notifyOneMember(event, memberId, message);
        }
    }

    /* 한 회원의 중복 저장 여부를 판정하고 새 알림일 때만 실시간으로 발행한다. */
    private void notifyOneMember(MeetingReservedEvent event, Long memberId, String message) {
        try {
            /* 회사·회원·타입·회의 조합을 가진 미확인 알림 도메인을 생성한다. */
            Notification notification = Notification.create(
                    event.companyId(),
                    memberId,
                    TYPE_MEETING_CREATED,
                    event.meetingId(),
                    message
            );

            /* DB 유일성 제약으로 이미 처리된 회원이면 SSE 중복 발행도 생략한다. */
            if (!notificationRepository.saveIfAbsent(notification)) {
                log.info("이미 보낸 회의 개설 알림 — 스킵. meetingId={} memberId={}",
                        event.meetingId(), memberId);
                return;
            }

            /* 프론트가 회의 화면과 시간을 바로 표시할 수 있는 payload를 구성한다. */
            NotificationEvent notificationEvent = new NotificationEvent(
                    TYPE_MEETING_CREATED,
                    new MeetingCreatedPayload(
                            event.meetingId(),
                            event.title(),
                            message,
                            event.startAt(),
                            event.endAt()
                    )
            );

            /* 연결이 없는 회원은 저장 이력만 남고, 연결된 회원은 개인 SSE로 즉시 받는다. */
            notificationPublishPort.publish(memberId, notificationEvent);
        } catch (RuntimeException exception) {
            /* 커밋 이후 한 회원의 저장·발행 실패는 로그로 남기고 다음 회원 처리를 계속한다. */
            log.error("회의 개설 알림 처리 실패 — meetingId={} memberId={}",
                    event.meetingId(), memberId, exception);
        }
    }

    /* 프론트의 MEETING_CREATED 분기에 전달하는 JSON 직렬화용 불변 payload다. */
    public record MeetingCreatedPayload(
            Long meetingId,
            String title,
            String message,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }
}
