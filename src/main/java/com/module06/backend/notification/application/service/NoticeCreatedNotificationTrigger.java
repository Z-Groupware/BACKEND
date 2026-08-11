package com.module06.backend.notification.application.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.module06.backend.notice.application.event.NoticeCreatedEvent;
import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.NotificationType;

/*
 * 공지 등록 커밋 이후 같은 회사 활성 회원 전체에게 상단 배너용 SSE 이벤트를 발행한다.
 */
@Component
public class NoticeCreatedNotificationTrigger {

    /* 커밋 이후 수신자 조회와 회원별 발행 실패를 기록하는 로거다. */
    private static final Logger log = LoggerFactory.getLogger(NoticeCreatedNotificationTrigger.class);

    /* 프론트 SSE 분기에 사용하는 공지 등록 이벤트 타입이다. */
    private static final String TYPE_NOTICE_CREATED = NotificationType.NOTICE_CREATED.name();

    /* 공지가 속한 회사의 비삭제 회원 식별자를 조회하는 Port다. */
    private final CompanyMemberQueryPort companyMemberQueryPort;

    /* 회사 회원별 개인 SSE 연결로 실시간 이벤트를 발행하는 Port다. */
    private final NotificationPublishPort notificationPublishPort;

    /* 수신자 조회와 개인 SSE 발행 의존성을 명시적으로 주입한다. */
    public NoticeCreatedNotificationTrigger(
            CompanyMemberQueryPort companyMemberQueryPort,
            NotificationPublishPort notificationPublishPort
    ) {
        /* 두 협력자를 커밋 이후 이벤트 처리에 사용하도록 보관한다. */
        this.companyMemberQueryPort = companyMemberQueryPort;
        this.notificationPublishPort = notificationPublishPort;
    }

    /* 공지 등록 트랜잭션이 실제 커밋된 경우에만 회사 전체 배너 알림을 처리한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticeCreated(NoticeCreatedEvent event) {
        /* 제목을 포함한 메시지와 공통 payload를 회원 반복 처리 전에 한 번만 만든다. */
        String message = event.title() + " 공지사항이 등록되었습니다.";
        NotificationEvent notificationEvent = new NotificationEvent(
                TYPE_NOTICE_CREATED,
                new NoticeCreatedPayload(
                        event.noticeId(),
                        event.title(),
                        message
                )
        );

        /* 회사 회원 조회 실패가 이미 커밋된 공지 등록 결과에 영향을 주지 않게 격리한다. */
        List<Long> recipientMemberIds;
        try {
            /* 이벤트의 회사 식별자를 사용해 수신자 범위를 한 테넌트 안에 고정한다. */
            recipientMemberIds = companyMemberQueryPort.findActiveMemberIds(event.companyId());
        } catch (RuntimeException exception) {
            /* 실패 원인을 기록하고 현재 공지의 실시간 배너 발행만 종료한다. */
            log.error("공지 등록 알림 수신자 조회 실패 — noticeId={} companyId={}",
                    event.noticeId(), event.companyId(), exception);
            return;
        }

        /* 같은 회사의 활성 회원 각각에게 동일한 공지 등록 이벤트를 발행한다. */
        for (Long memberId : recipientMemberIds) {
            /* 한 회원의 Redis·SSE 실패가 나머지 회원 발행을 막지 않도록 개별 처리한다. */
            publishToOneMember(event, memberId, notificationEvent);
        }
    }

    /* 한 회원에게 공지 배너 이벤트를 best-effort로 발행한다. */
    private void publishToOneMember(
            NoticeCreatedEvent sourceEvent,
            Long memberId,
            NotificationEvent notificationEvent
    ) {
        try {
            /* 미접속 회원은 Port 정책에 따라 조용히 버리고 연결된 회원에게 즉시 전달한다. */
            notificationPublishPort.publish(memberId, notificationEvent);
        } catch (RuntimeException exception) {
            /* 회원별 발행 실패를 기록하고 다음 수신자 처리를 계속한다. */
            log.error("공지 등록 SSE 발행 실패 — noticeId={} memberId={}",
                    sourceEvent.noticeId(), memberId, exception);
        }
    }

    /* 프론트의 NOTICE_CREATED 배너 분기에 전달하는 JSON 직렬화용 불변 payload다. */
    public record NoticeCreatedPayload(
            Long noticeId,
            String title,
            String message
    ) {
    }
}
