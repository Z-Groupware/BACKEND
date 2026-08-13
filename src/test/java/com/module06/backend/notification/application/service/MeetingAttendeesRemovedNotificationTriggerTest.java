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

import com.module06.backend.meeting.application.event.MeetingAttendeesRemovedEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * MEET-09 참석자 제외 이벤트를 받아 명단에서 빠진 구성원 전원에게 저장 후 발행하는지, 이미
 * 저장된(중복) 회원은 실시간 발행을 건너뛰는지, 한 회원 처리 실패가 나머지 회원 처리를
 * 막지 않는지 검증한다.
 */
@DisplayName("회의 참석자 제외 알림 트리거")
class MeetingAttendeesRemovedNotificationTriggerTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MEETING_ID = 500L;

    /* 제외된 구성원 전원에게 저장 성공 시 실시간 발행까지 되는지 검증한다. */
    @Test
    @DisplayName("제외된 구성원 전원에게 저장하고 발행한다")
    void savesAndPublishesToAllRemovedAttendees() {
        List<Long> savedMemberIds = new ArrayList<>();
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingAttendeesRemovedNotificationTrigger trigger = trigger(
                notification -> { savedMemberIds.add(notification.getMemberId()); return true; },
                (memberId, event) -> publishedMemberIds.add(memberId));

        trigger.onMeetingAttendeesRemoved(event(List.of(11L, 15L)));

        assertThat(savedMemberIds).containsExactlyInAnyOrder(11L, 15L);
        assertThat(publishedMemberIds).containsExactlyInAnyOrder(11L, 15L);
    }

    /* 저장(saveIfAbsent)이 false(이미 있던 알림)면 그 회원에게는 실시간 발행을 안 하는지 검증한다. */
    @Test
    @DisplayName("이미 저장된 회원은 실시간 발행을 건너뛴다")
    void skipsPublishForAlreadySavedMember() {
        Set<Long> alreadyNotified = new HashSet<>(Set.of(15L));
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingAttendeesRemovedNotificationTrigger trigger = trigger(
                notification -> !alreadyNotified.contains(notification.getMemberId()),
                (memberId, event) -> publishedMemberIds.add(memberId));

        trigger.onMeetingAttendeesRemoved(event(List.of(11L, 15L)));

        assertThat(publishedMemberIds).containsExactly(11L);
    }

    /* 한 회원 처리 중 예외가 나도(저장 실패 등) 나머지 회원은 계속 처리되는지 검증한다. */
    @Test
    @DisplayName("한 회원 처리가 실패해도 나머지 회원은 계속 처리한다")
    void oneFailureDoesNotBlockOthers() {
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingAttendeesRemovedNotificationTrigger trigger = trigger(
                notification -> {
                    if (notification.getMemberId().equals(11L)) {
                        throw new RuntimeException("저장 중 예상치 못한 오류");
                    }
                    return true;
                },
                (memberId, event) -> publishedMemberIds.add(memberId));

        assertThatCode(() -> trigger.onMeetingAttendeesRemoved(event(List.of(11L, 15L))))
                .doesNotThrowAnyException();
        assertThat(publishedMemberIds).containsExactly(15L);
    }

    /* 발행되는 알림의 type·payload가 기대한 형태(제목 포함 메시지)인지 검증한다. */
    @Test
    @DisplayName("발행 이벤트는 MEETING_ATTENDEE_REMOVED 타입과 제목이 포함된 메시지를 담는다")
    void publishedEventHasExpectedShape() {
        List<NotificationEvent> published = new ArrayList<>();
        MeetingAttendeesRemovedNotificationTrigger trigger = trigger(
                notification -> true,
                (memberId, event) -> published.add(event));

        trigger.onMeetingAttendeesRemoved(event(List.of(15L)));

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("MEETING_ATTENDEE_REMOVED");
        assertThat(published.get(0).payload())
                .isInstanceOf(MeetingAttendeesRemovedNotificationTrigger.MeetingAttendeeRemovedPayload.class);
        var payload =
                (MeetingAttendeesRemovedNotificationTrigger.MeetingAttendeeRemovedPayload) published.get(0).payload();
        assertThat(payload.message()).isEqualTo("스프린트 회고 회의 참석자 명단에서 제외되었습니다.");
        assertThat(payload.meetingId()).isEqualTo(MEETING_ID);
    }

    private MeetingAttendeesRemovedEvent event(List<Long> removedAttendeeMemberIds) {
        return new MeetingAttendeesRemovedEvent(MEETING_ID, COMPANY_ID, 3L, removedAttendeeMemberIds, "스프린트 회고",
                LocalDateTime.of(2026, 8, 10, 14, 0), LocalDateTime.of(2026, 8, 10, 15, 0));
    }

    private MeetingAttendeesRemovedNotificationTrigger trigger(
            java.util.function.Predicate<Notification> saveIfAbsent,
            NotificationPublishPort publishPort
    ) {
        NotificationRepository repository = saveIfAbsent::test;
        return new MeetingAttendeesRemovedNotificationTrigger(repository, publishPort);
    }
}
