package com.module06.backend.notification.application.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.module06.backend.meeting.application.event.MeetingCanceledEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * MEET-06(회의 취소)이 발행한 MeetingCanceledEvent를 받아 참석자 전원에게 알림을 저장하고
 * 실시간으로 보낸다. capture의 MeetingCompletedAnalysisTrigger와 동일한 D→소비 도메인 이벤트
 * 연동 패턴이다.
 *
 * <h2>왜 AFTER_COMMIT인가</h2>
 * D의 계약(MeetingCancellationService 주석)이 "최초 취소 트랜잭션 안에서 발행해 F가 AFTER_COMMIT으로만
 * 처리하게 한다"고 명시한다 — 취소가 롤백되면 알림도 나가면 안 되므로, 커밋 확정 후에만 반응한다.
 *
 * <h2>먼저 저장, 그다음 실시간 발행</h2>
 * 모성진(D) 요청 — 저장이 실제 테넌트 격리·중복 방지의 기준이고, SSE는 그 저장이 성공한 뒤의
 * 실시간 알림 수단일 뿐이다. saveIfAbsent()가 false(이미 있던 알림)면 그 회원에게는 publish도
 * 안 한다 — 이미 받은 알림을 다시 실시간으로 밀어줄 이유가 없다.
 *
 * <h2>meeting/member 테이블을 재조회하지 않는다</h2>
 * companyId/memberId는 이벤트에 실린 값을 그대로 신뢰한다(D가 이미 검증) — 애플리케이션이 따로
 * 조회해서 재검증하지 않는다. notification 테이블의 company_id는 FK(company 참조)로 "실존하는
 * 회사인지"만 DB 레벨에서 보장한다. recipient_member_id는 FK가 없다(V1 baseline부터 원래 없었고
 * 이번에 추가하지 않았다) — 잘못된 memberId가 와도 DB가 막아주지 않으므로, 이 값이 안전한 건
 * 순전히 "D가 실제 참석자 목록에서만 이벤트를 만든다"는 신뢰에 의존한다.
 *
 * <h2>다른 회사 데이터가 섞일 수 없는 구조</h2>
 * 한 이벤트 안의 모든 수신자는 항상 같은 companyId(event.companyId())를 쓴다 — 회의 하나는 한
 * 회사 소속이므로, 이 트리거 내부에서 서로 다른 회사가 섞일 경로 자체가 없다. companyId가 null인
 * 경우는 Notification.create()가 즉시 IllegalArgumentException으로 막는다(회사 불명 알림을
 * 저장하지 않는다).
 *
 * <h2>개설자를 따로 챙기지 않는다</h2>
 * Meeting.create()가 attendeeMemberIds에 hostMemberId를 항상 먼저 넣어두므로(개설자도 참석자 목록의
 * 일원), event.attendeeMemberIds()만 순회하면 개설자도 이미 포함돼 있다. hostMemberId 필드를 여기서
 * 따로 안 쓰는 건 실수가 아니라 중복 알림 방지다.
 *
 * <h2>D를 부르지 않는다</h2>
 * 이 클래스는 이벤트 레코드 하나만 안다(capture 쪽과 동일 원칙) — meeting 도메인의 다른 클래스는
 * 참조하지 않는다.
 *
 * <h2>여기서 던지지 않는다</h2>
 * AFTER_COMMIT 리스너의 예외는 이미 커밋된 트랜잭션을 못 되돌리고 요청자에게도 안 닿는다 — 실패는
 * 로그로만 남긴다(capture 쪽과 동일 원칙). 한 회원 처리 중 예외가 나도 나머지 회원은 계속
 * 처리한다 — 한 명 실패로 전원이 알림을 못 받으면 안 된다.
 */
@Component
public class MeetingCanceledNotificationTrigger {

    private static final Logger log = LoggerFactory.getLogger(MeetingCanceledNotificationTrigger.class);
    private static final String TYPE_MEETING_CANCELED = "MEETING_CANCELED";

    private final NotificationRepository notificationRepository;
    private final NotificationPublishPort notificationPublishPort;

    public MeetingCanceledNotificationTrigger(NotificationRepository notificationRepository,
                                              NotificationPublishPort notificationPublishPort) {
        this.notificationRepository = notificationRepository;
        this.notificationPublishPort = notificationPublishPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingCanceled(MeetingCanceledEvent event) {
        String message = event.title() + " 회의가 취소되었습니다.";
        for (Long memberId : event.attendeeMemberIds()) {
            notifyOneMember(event, memberId, message);
        }
    }

    private void notifyOneMember(MeetingCanceledEvent event, Long memberId, String message) {
        try {
            Notification notification = Notification.create(
                    event.companyId(), memberId, TYPE_MEETING_CANCELED, event.meetingId(), message);

            if (!notificationRepository.saveIfAbsent(notification)) {
                log.info("이미 보낸 회의 취소 알림 — 스킵. meetingId={} memberId={}", event.meetingId(), memberId);
                return;
            }

            NotificationEvent payload = new NotificationEvent(TYPE_MEETING_CANCELED,
                    new MeetingCanceledPayload(event.meetingId(), message, event.startAt(), event.canceledAt()));
            notificationPublishPort.publish(memberId, payload);
        } catch (RuntimeException e) {
            // 던져봐야 갈 곳이 없다(클래스 주석). 이 회원만 실패로 남기고 나머지는 계속 처리한다.
            log.error("회의 취소 알림 처리 실패 — meetingId={} memberId={}", event.meetingId(), memberId, e);
        }
    }

    /** 클라이언트로 나가는 회의 취소 알림 payload. */
    public record MeetingCanceledPayload(Long meetingId, String message, LocalDateTime startAt,
                                         LocalDateTime canceledAt) {
    }
}
