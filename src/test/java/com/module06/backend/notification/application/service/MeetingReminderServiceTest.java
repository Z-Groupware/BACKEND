package com.module06.backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;
import com.module06.backend.notification.application.port.out.MeetingReminderQueryPort;
import com.module06.backend.notification.application.port.out.MeetingReminderQueryPort.MeetingReminderTarget;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * 회의 10분 전 시간창·회사 전체 수신자·중복 방지·장애 격리 계약을 검증한다.
 */
@DisplayName("회의 10분 전 알림 서비스")
class MeetingReminderServiceTest {

    /* 테스트에서 시스템 타임존과 무관하게 KST 로컬 시각을 해석하는 타임존이다. */
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    /* 2026-08-10 13:20:35 KST로 고정해 13:30~13:31 시간창을 검증하는 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T04:20:35Z"),
            KOREA_ZONE
    );

    /* 알림 대상 조회가 현재 분의 정확히 10분 뒤 반개구간을 사용하는지 검증한다. */
    @Test
    @DisplayName("초를 버리고 10분 뒤의 한 분 시간창만 조회한다")
    void queriesExactTenMinuteWindow() {
        /* Port에 전달된 시작과 종료 경계를 보관한다. */
        AtomicReference<LocalDateTime> capturedFrom = new AtomicReference<>();
        AtomicReference<LocalDateTime> capturedTo = new AtomicReference<>();

        /* 조회 경계만 기록하고 대상은 없다고 반환하는 서비스 대역을 조립한다. */
        MeetingReminderService service = service(
                (fromInclusive, toExclusive) -> {
                    capturedFrom.set(fromInclusive);
                    capturedTo.set(toExclusive);
                    return List.of();
                },
                companyId -> List.of(),
                notification -> true,
                (memberId, event) -> {
                    throw new AssertionError("대상 회의가 없으면 발행하면 안 됩니다.");
                }
        );

        /* 고정 시계의 현재 분을 기준으로 알림 실행을 요청한다. */
        service.sendReminders();

        /* 시작 경계는 13:30 포함, 종료 경계는 13:31 제외여야 한다. */
        assertThat(capturedFrom.get()).isEqualTo(LocalDateTime.of(2026, 8, 10, 13, 30));
        assertThat(capturedTo.get()).isEqualTo(LocalDateTime.of(2026, 8, 10, 13, 31));
    }

    /* 같은 회사의 여러 회의가 있어도 회사 회원 전체를 한 번 조회해 모두 알리는지 검증한다. */
    @Test
    @DisplayName("같은 회사 활성 회원 전체에게 저장·발행하고 수신자 조회를 재사용한다")
    void notifiesAllActiveCompanyMembersAndCachesRecipients() {
        /* 같은 회사의 동일 시간창 회의 두 건을 준비한다. */
        List<MeetingReminderTarget> targets = List.of(
                target(501L, "스프린트 회고", 21L, "대회의실"),
                target(502L, "주간 계획", 22L, "소회의실")
        );
        AtomicInteger recipientQueryCount = new AtomicInteger();
        List<Notification> savedNotifications = new ArrayList<>();
        List<PublishedNotification> publishedNotifications = new ArrayList<>();

        /* 회사 구성원 세 명에게 두 회의 알림을 저장·발행하는 서비스 대역을 조립한다. */
        MeetingReminderService service = service(
                (fromInclusive, toExclusive) -> targets,
                companyId -> {
                    recipientQueryCount.incrementAndGet();
                    return List.of(3L, 7L, 9L);
                },
                notification -> {
                    savedNotifications.add(notification);
                    return true;
                },
                (memberId, event) -> publishedNotifications.add(new PublishedNotification(memberId, event))
        );

        /* 한 번의 분 단위 알림 실행을 수행한다. */
        service.sendReminders();

        /* 같은 회사 회원 조회는 한 번이고 회의 2건과 회원 3명의 조합 6건이 처리돼야 한다. */
        assertThat(recipientQueryCount).hasValue(1);
        assertThat(savedNotifications).hasSize(6);
        assertThat(publishedNotifications).hasSize(6);
        assertThat(savedNotifications)
                .extracting(Notification::getMemberId)
                .containsExactly(3L, 7L, 9L, 3L, 7L, 9L);
        assertThat(savedNotifications)
                .extracting(Notification::getType)
                .containsOnly("MEETING_REMINDER");

        /* 첫 발행 payload에는 프론트 표시와 이동에 필요한 회의·회의실 정보가 포함돼야 한다. */
        NotificationEvent firstEvent = publishedNotifications.get(0).event();
        assertThat(firstEvent.type()).isEqualTo("MEETING_REMINDER");
        assertThat(firstEvent.payload()).isInstanceOf(MeetingReminderService.MeetingReminderPayload.class);
        var payload = (MeetingReminderService.MeetingReminderPayload) firstEvent.payload();
        assertThat(payload.meetingId()).isEqualTo(501L);
        assertThat(payload.title()).isEqualTo("스프린트 회고");
        assertThat(payload.message()).isEqualTo("스프린트 회고 회의가 10분 후 시작됩니다.");
        assertThat(payload.meetingRoomId()).isEqualTo(21L);
        assertThat(payload.meetingRoomName()).isEqualTo("대회의실");
    }

    /* 이미 처리된 회원과 실패한 회원이 다른 수신자의 발행을 방해하지 않는지 검증한다. */
    @Test
    @DisplayName("중복과 회원별 실패를 격리하고 나머지 회원은 계속 발행한다")
    void isolatesDuplicateAndPerMemberFailure() {
        /* 7번은 중복, 9번은 저장 실패로 가정하고 실제 발행 회원만 기록한다. */
        Set<Long> duplicateMemberIds = new HashSet<>(Set.of(7L));
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingReminderService service = service(
                (fromInclusive, toExclusive) -> List.of(target(501L, "스프린트 회고", 21L, "대회의실")),
                companyId -> List.of(3L, 7L, 9L, 11L),
                notification -> {
                    if (notification.getMemberId().equals(9L)) {
                        throw new RuntimeException("회원별 저장 실패");
                    }
                    return !duplicateMemberIds.contains(notification.getMemberId());
                },
                (memberId, event) -> publishedMemberIds.add(memberId)
        );

        /* 중복과 저장 실패가 있어도 실행 전체에 예외가 전파되지 않아야 한다. */
        assertThatCode(service::sendReminders).doesNotThrowAnyException();

        /* 신규 저장에 성공한 앞뒤 회원만 SSE 발행 대상이어야 한다. */
        assertThat(publishedMemberIds).containsExactly(3L, 11L);
    }

    /* 전체 회의 조회 실패가 스케줄러 호출자에게 전파되지 않는지 검증한다. */
    @Test
    @DisplayName("대상 회의 조회 실패는 로그로 남기고 실행을 종료한다")
    void targetQueryFailureDoesNotEscape() {
        /* 회의 조회에서 인프라 장애가 발생하는 서비스 대역을 준비한다. */
        MeetingReminderService service = service(
                (fromInclusive, toExclusive) -> {
                    throw new RuntimeException("회의 조회 실패");
                },
                companyId -> {
                    throw new AssertionError("회의 조회 실패 뒤에는 회원을 조회하면 안 됩니다.");
                },
                notification -> {
                    throw new AssertionError("회의 조회 실패 뒤에는 저장하면 안 됩니다.");
                },
                (memberId, event) -> {
                    throw new AssertionError("회의 조회 실패 뒤에는 발행하면 안 됩니다.");
                }
        );

        /* 다음 분 스케줄 실행을 보장하기 위해 현재 실행은 예외 없이 끝나야 한다. */
        assertThatCode(service::sendReminders).doesNotThrowAnyException();
    }

    /* 테스트마다 같은 회사와 시작 시각을 가진 알림 대상 회의를 생성한다. */
    private MeetingReminderTarget target(
            Long meetingId,
            String title,
            Long meetingRoomId,
            String meetingRoomName
    ) {
        /* 고정 시간창 안의 13:30 회의를 알림 읽기 모델로 반환한다. */
        return new MeetingReminderTarget(
                1L,
                meetingId,
                title,
                LocalDateTime.of(2026, 8, 10, 13, 30),
                LocalDateTime.of(2026, 8, 10, 14, 0),
                meetingRoomId,
                meetingRoomName
        );
    }

    /* 함수형 대역으로 서비스의 다섯 의존성을 간결하게 조립한다. */
    private MeetingReminderService service(
            MeetingReminderQueryPort meetingReminderQueryPort,
            CompanyMemberQueryPort companyMemberQueryPort,
            java.util.function.Predicate<Notification> saveIfAbsent,
            NotificationPublishPort notificationPublishPort
    ) {
        /* Predicate 결과를 실제 NotificationRepository 계약으로 감싼다. */
        NotificationRepository notificationRepository = saveIfAbsent::test;
        return new MeetingReminderService(
                meetingReminderQueryPort,
                companyMemberQueryPort,
                notificationRepository,
                notificationPublishPort,
                FIXED_CLOCK
        );
    }

    /* 회원 식별자와 발행 이벤트를 함께 검증하기 위한 테스트 전용 값 객체다. */
    private record PublishedNotification(Long memberId, NotificationEvent event) {
    }
}
