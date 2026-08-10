package com.module06.backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * 회의 개설 이벤트가 참석자에 한정되지 않고 같은 회사 구성원 전체에게 저장·발행되는지 검증한다.
 */
@DisplayName("회의 개설 알림 트리거")
class MeetingCreatedNotificationTriggerTest {

    /* 테스트 이벤트가 속한 회사 식별자다. */
    private static final Long COMPANY_ID = 1L;

    /* 회원별 중복 키와 payload 검증에 사용하는 회의 식별자다. */
    private static final Long MEETING_ID = 500L;

    /* 회사 구성원 전체에게 저장한 뒤 개인 SSE 발행 Port를 호출하는지 검증한다. */
    @Test
    @DisplayName("같은 회사의 활성 회원 전체에게 저장하고 발행한다")
    void savesAndPublishesToAllActiveCompanyMembers() {
        /* 이벤트 참석자와 다른 회사 구성원 목록을 준비해 수신자 기준이 회사 전체인지 구분한다. */
        List<Long> activeMemberIds = List.of(3L, 7L, 9L);
        List<Long> savedMemberIds = new ArrayList<>();
        List<Long> publishedMemberIds = new ArrayList<>();

        /* 구성원 조회·저장·발행 결과를 기록하는 트리거 대역을 조립한다. */
        MeetingCreatedNotificationTrigger trigger = trigger(
                companyId -> activeMemberIds,
                notification -> {
                    savedMemberIds.add(notification.getMemberId());
                    return true;
                },
                (memberId, event) -> publishedMemberIds.add(memberId)
        );

        /* 참석자는 개설자 한 명뿐이지만 회사 구성원 세 명 모두 알림 대상이어야 한다. */
        trigger.onMeetingCreated(event(List.of(3L)));

        /* 저장과 발행 모두 회사 구성원 조회 결과 전체를 사용해야 한다. */
        assertThat(savedMemberIds).containsExactly(3L, 7L, 9L);
        assertThat(publishedMemberIds).containsExactly(3L, 7L, 9L);
    }

    /* 이미 저장된 회원의 알림을 실시간으로 다시 발행하지 않는지 검증한다. */
    @Test
    @DisplayName("이미 저장된 회원은 SSE 중복 발행을 건너뛴다")
    void skipsPublishForAlreadySavedMember() {
        /* 7번 회원은 같은 회의 개설 알림을 이미 저장했다고 가정한다. */
        Set<Long> alreadyNotified = new HashSet<>(Set.of(7L));
        List<Long> publishedMemberIds = new ArrayList<>();

        /* saveIfAbsent 결과가 false인 회원만 발행 대상에서 제외하는 트리거를 준비한다. */
        MeetingCreatedNotificationTrigger trigger = trigger(
                companyId -> List.of(3L, 7L, 9L),
                notification -> !alreadyNotified.contains(notification.getMemberId()),
                (memberId, event) -> publishedMemberIds.add(memberId)
        );

        /* 동일 회의 이벤트를 처리한다. */
        trigger.onMeetingCreated(event(List.of(3L)));

        /* 신규 저장된 3번과 9번 회원에게만 SSE가 발행돼야 한다. */
        assertThat(publishedMemberIds).containsExactly(3L, 9L);
    }

    /* 한 회원의 저장 실패가 나머지 회사 구성원 처리를 중단하지 않는지 검증한다. */
    @Test
    @DisplayName("한 회원 처리가 실패해도 나머지 회원은 계속 처리한다")
    void oneMemberFailureDoesNotBlockOthers() {
        /* 실제 발행까지 도달한 회원을 기록한다. */
        List<Long> publishedMemberIds = new ArrayList<>();

        /* 7번 회원 저장에서만 예외가 발생하는 저장소 대역을 사용한다. */
        MeetingCreatedNotificationTrigger trigger = trigger(
                companyId -> List.of(3L, 7L, 9L),
                notification -> {
                    if (notification.getMemberId().equals(7L)) {
                        throw new RuntimeException("회원별 알림 저장 실패");
                    }
                    return true;
                },
                (memberId, event) -> publishedMemberIds.add(memberId)
        );

        /* AFTER_COMMIT 처리 실패가 호출자에게 전파되지 않아야 한다. */
        assertThatCode(() -> trigger.onMeetingCreated(event(List.of(3L))))
                .doesNotThrowAnyException();

        /* 실패한 회원을 제외한 앞뒤 회원은 정상적으로 발행돼야 한다. */
        assertThat(publishedMemberIds).containsExactly(3L, 9L);
    }

    /* 회사 구성원 조회 자체가 실패해도 커밋된 회의 요청에 예외를 전파하지 않는지 검증한다. */
    @Test
    @DisplayName("수신자 조회 실패는 로그로 남기고 종료한다")
    void recipientQueryFailureDoesNotEscapeListener() {
        /* 수신자 조회 시 인프라 예외를 발생시키는 Port 대역을 준비한다. */
        MeetingCreatedNotificationTrigger trigger = trigger(
                companyId -> {
                    throw new RuntimeException("구성원 조회 실패");
                },
                notification -> {
                    throw new AssertionError("수신자 조회 실패 뒤에는 저장하면 안 됩니다.");
                },
                (memberId, event) -> {
                    throw new AssertionError("수신자 조회 실패 뒤에는 발행하면 안 됩니다.");
                }
        );

        /* 이미 커밋된 회의 작업과 분리된 리스너는 예외 없이 종료해야 한다. */
        assertThatCode(() -> trigger.onMeetingCreated(event(List.of(3L))))
                .doesNotThrowAnyException();
    }

    /* 프론트가 분기하고 화면을 구성할 type과 payload 계약을 검증한다. */
    @Test
    @DisplayName("MEETING_CREATED 타입과 회의 표시 정보를 발행한다")
    void publishesMeetingCreatedPayload() {
        /* 발행된 이벤트 객체를 보관할 목록을 준비한다. */
        List<NotificationEvent> publishedEvents = new ArrayList<>();

        /* 한 회원에게 정상 저장·발행되는 트리거를 조립한다. */
        MeetingCreatedNotificationTrigger trigger = trigger(
                companyId -> List.of(7L),
                notification -> true,
                (memberId, event) -> publishedEvents.add(event)
        );

        /* 회의 개설 이벤트 한 건을 처리한다. */
        trigger.onMeetingCreated(event(List.of(3L)));

        /* SSE 최상위 type은 프론트 계약인 MEETING_CREATED여야 한다. */
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).type()).isEqualTo("MEETING_CREATED");
        assertThat(publishedEvents.get(0).payload())
                .isInstanceOf(MeetingCreatedNotificationTrigger.MeetingCreatedPayload.class);

        /* payload에는 회의 이동·표시에 필요한 식별자·제목·시간·메시지가 모두 포함돼야 한다. */
        var payload = (MeetingCreatedNotificationTrigger.MeetingCreatedPayload) publishedEvents.get(0).payload();
        assertThat(payload.meetingId()).isEqualTo(MEETING_ID);
        assertThat(payload.title()).isEqualTo("스프린트 회고");
        assertThat(payload.message()).isEqualTo("스프린트 회고 회의가 개설되었습니다.");
        assertThat(payload.startAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 14, 0));
        assertThat(payload.endAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 15, 0));
    }

    /* 테스트마다 같은 회사·회의 표시값을 가진 예약 완료 이벤트를 만든다. */
    private MeetingReservedEvent event(List<Long> attendeeMemberIds) {
        /* 참석자 목록은 회사 전체 수신자 조회와 구분할 수 있도록 테스트별로 전달받는다. */
        return new MeetingReservedEvent(
                MEETING_ID,
                COMPANY_ID,
                3L,
                attendeeMemberIds,
                "스프린트 회고",
                LocalDateTime.of(2026, 8, 10, 14, 0),
                LocalDateTime.of(2026, 8, 10, 15, 0),
                true
        );
    }

    /* 함수형 대역으로 테스트 대상 Trigger를 간결하게 조립한다. */
    private MeetingCreatedNotificationTrigger trigger(
            CompanyMemberQueryPort companyMemberQueryPort,
            java.util.function.Predicate<Notification> saveIfAbsent,
            NotificationPublishPort notificationPublishPort
    ) {
        /* Predicate 결과를 실제 NotificationRepository 계약으로 감싼다. */
        NotificationRepository notificationRepository = saveIfAbsent::test;
        return new MeetingCreatedNotificationTrigger(
                companyMemberQueryPort,
                notificationRepository,
                notificationPublishPort
        );
    }
}
