package com.module06.backend.notification.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;
import com.module06.backend.notification.application.port.out.MeetingReminderQueryPort;
import com.module06.backend.notification.application.port.out.MeetingReminderQueryPort.MeetingReminderTarget;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.application.usecase.SendMeetingRemindersUseCase;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.model.NotificationType;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * 매분 현재 시각의 정확히 10분 뒤 시작하는 회의를 찾아 회사 활성 회원 전체에게 알림을 보낸다.
 */
@Service
public class MeetingReminderService implements SendMeetingRemindersUseCase {

    /* 스케줄 실행 실패와 회원별 격리 결과를 기록하는 로거다. */
    private static final Logger log = LoggerFactory.getLogger(MeetingReminderService.class);

    /* 회의 시작 전에 알림을 발송할 고정 선행 시간이다. */
    private static final long REMINDER_MINUTES_BEFORE = 10L;

    /* 데이터베이스 중복 키와 프론트 SSE 분기에 함께 사용하는 알림 타입이다. */
    private static final String TYPE_MEETING_REMINDER = NotificationType.MEETING_REMINDER.name();

    /* 예약 상태와 시작 시각으로 알림 대상 회의를 조회하는 Port다. */
    private final MeetingReminderQueryPort meetingReminderQueryPort;

    /* 대상 회사의 비삭제 회원 전체를 조회하는 Port다. */
    private final CompanyMemberQueryPort companyMemberQueryPort;

    /* 회원별 알림 이력을 중복 방지 제약과 함께 저장하는 저장소다. */
    private final NotificationRepository notificationRepository;

    /* 신규 저장된 알림을 개인 SSE 연결로 발행하는 Port다. */
    private final NotificationPublishPort notificationPublishPort;

    /* 시스템 기본 타임존과 테스트 실행 시각에 의존하지 않는 현재 시각 공급자다. */
    private final Clock clock;

    /* 회의 조회·회원 조회·저장·발행·시간 의존성을 명시적으로 주입한다. */
    public MeetingReminderService(
            MeetingReminderQueryPort meetingReminderQueryPort,
            CompanyMemberQueryPort companyMemberQueryPort,
            NotificationRepository notificationRepository,
            NotificationPublishPort notificationPublishPort,
            Clock clock
    ) {
        /* 각 협력자를 필드에 저장해 스케줄 실행마다 재사용한다. */
        this.meetingReminderQueryPort = meetingReminderQueryPort;
        this.companyMemberQueryPort = companyMemberQueryPort;
        this.notificationRepository = notificationRepository;
        this.notificationPublishPort = notificationPublishPort;
        this.clock = clock;
    }

    /* 현재 분을 기준으로 10분 뒤 한 분 동안 시작하는 회의만 조회해 알림을 처리한다. */
    @Override
    public void sendReminders() {
        /* 초·나노초를 버려 스케줄러가 몇 초 늦게 실행돼도 같은 분의 시간창을 사용한다. */
        LocalDateTime currentMinute = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime fromInclusive = currentMinute.plusMinutes(REMINDER_MINUTES_BEFORE);
        LocalDateTime toExclusive = fromInclusive.plusMinutes(1L);

        /* 회의 조회 장애는 다음 분 스케줄 실행과 분리하고 현재 실행만 안전하게 종료한다. */
        List<MeetingReminderTarget> targets;
        try {
            /* 반개구간으로 조회해 경계 회의가 인접 실행에서 두 번 후보가 되지 않게 한다. */
            targets = meetingReminderQueryPort.findScheduledMeetingsStartingBetween(
                    fromInclusive,
                    toExclusive
            );
        } catch (RuntimeException exception) {
            /* 전체 대상 조회 실패를 로그로 남기고 스케줄러 스레드 밖으로 전파하지 않는다. */
            log.error("회의 10분 전 알림 대상 조회 실패 — from={} to={}",
                    fromInclusive, toExclusive, exception);
            return;
        }

        /* 같은 회사의 여러 회의가 있어도 활성 회원 조회는 실행당 한 번만 수행한다. */
        Map<Long, List<Long>> recipientCache = new HashMap<>();
        for (MeetingReminderTarget target : targets) {
            /* 회사별 수신자 조회 실패는 해당 회사 회의만 건너뛰고 다른 회사는 계속 처리한다. */
            List<Long> recipientMemberIds = findRecipients(target, recipientCache);
            if (recipientMemberIds == null) {
                continue;
            }

            /* 제목을 포함한 메시지를 회의별 한 번만 만들고 모든 회사 회원에게 재사용한다. */
            String message = target.title() + " 회의가 10분 후 시작됩니다.";
            for (Long memberId : recipientMemberIds) {
                /* 한 회원의 저장 또는 발행 실패가 나머지 수신자를 막지 않도록 개별 처리한다. */
                notifyOneMember(target, memberId, message);
            }
        }
    }

    /* 회사의 활성 회원 목록을 조회하고 같은 실행 안의 후속 회의에서 재사용한다. */
    private List<Long> findRecipients(
            MeetingReminderTarget target,
            Map<Long, List<Long>> recipientCache
    ) {
        /* 이미 성공적으로 조회한 회사는 추가 외부 조회 없이 캐시 결과를 반환한다. */
        if (recipientCache.containsKey(target.companyId())) {
            return recipientCache.get(target.companyId());
        }

        try {
            /* 회의가 속한 회사 식별자로 수신자 범위를 테넌트 안에 고정한다. */
            List<Long> recipientMemberIds = companyMemberQueryPort.findActiveMemberIds(target.companyId());
            recipientCache.put(target.companyId(), recipientMemberIds);
            return recipientMemberIds;
        } catch (RuntimeException exception) {
            /* 다른 회사 알림은 계속 보낼 수 있도록 실패한 회사만 건너뛴다. */
            log.error("회의 10분 전 알림 수신자 조회 실패 — meetingId={} companyId={}",
                    target.meetingId(), target.companyId(), exception);
            return null;
        }
    }

    /* 한 회원의 알림 이력을 먼저 저장하고 신규 이력일 때만 SSE로 발행한다. */
    private void notifyOneMember(MeetingReminderTarget target, Long memberId, String message) {
        try {
            /* 회사·회원·타입·회의 조합을 가진 미확인 알림 도메인을 생성한다. */
            Notification notification = Notification.create(
                    target.companyId(),
                    memberId,
                    TYPE_MEETING_REMINDER,
                    target.meetingId(),
                    message
            );

            /* 다중 인스턴스 또는 재실행에서도 DB 유일성 제약으로 중복 발행을 막는다. */
            if (!notificationRepository.saveIfAbsent(notification)) {
                log.info("이미 보낸 회의 10분 전 알림 — 스킵. meetingId={} memberId={}",
                        target.meetingId(), memberId);
                return;
            }

            /* 프론트가 회의와 회의실 정보를 즉시 표시할 수 있는 SSE payload를 만든다. */
            NotificationEvent notificationEvent = new NotificationEvent(
                    TYPE_MEETING_REMINDER,
                    new MeetingReminderPayload(
                            target.meetingId(),
                            target.title(),
                            message,
                            target.startAt(),
                            target.endAt(),
                            target.meetingRoomId(),
                            target.meetingRoomName()
                    )
            );

            /* 연결 중인 회원에게만 실시간 발행하고 미접속 회원은 저장 이력으로 보존한다. */
            notificationPublishPort.publish(memberId, notificationEvent);
        } catch (RuntimeException exception) {
            /* 한 회원의 실패를 기록한 뒤 다음 회원 알림 처리를 계속한다. */
            log.error("회의 10분 전 알림 처리 실패 — meetingId={} memberId={}",
                    target.meetingId(), memberId, exception);
        }
    }

    /* 프론트의 MEETING_REMINDER 분기에 전달하는 JSON 직렬화용 불변 payload다. */
    public record MeetingReminderPayload(
            Long meetingId,
            String title,
            String message,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long meetingRoomId,
            String meetingRoomName
    ) {
    }
}
