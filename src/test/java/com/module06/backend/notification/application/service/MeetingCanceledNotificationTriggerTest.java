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

import com.module06.backend.meeting.application.event.MeetingCanceledEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * MEET-06 회의 취소 이벤트를 받아 참석자 전원에게 저장 후 발행하는지, 이미 저장된(중복) 회원은
 * 실시간 발행을 건너뛰는지, 한 회원 처리 실패가 나머지 회원 처리를 막지 않는지 검증한다.
 */
@DisplayName("회의 취소 알림 트리거")
class MeetingCanceledNotificationTriggerTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MEETING_ID = 500L;

    /* 참석자 전원에게 저장 성공 시 실시간 발행까지 되는지 검증한다. */
    @Test
    @DisplayName("참석자 전원에게 저장하고 발행한다")
    void savesAndPublishesToAllAttendees() {
        List<Long> savedMemberIds = new ArrayList<>();
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingCanceledNotificationTrigger trigger = trigger(
                notification -> { savedMemberIds.add(notification.getMemberId()); return true; },
                (memberId, event) -> publishedMemberIds.add(memberId));

        trigger.onMeetingCanceled(event(List.of(3L, 7L, 9L)));

        assertThat(savedMemberIds).containsExactlyInAnyOrder(3L, 7L, 9L);
        assertThat(publishedMemberIds).containsExactlyInAnyOrder(3L, 7L, 9L);
    }

    /* 저장(saveIfAbsent)이 false(이미 있던 알림)면 그 회원에게는 실시간 발행을 안 하는지 검증한다. */
    @Test
    @DisplayName("이미 저장된 회원은 실시간 발행을 건너뛴다")
    void skipsPublishForAlreadySavedMember() {
        Set<Long> alreadyNotified = new HashSet<>(Set.of(7L));
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingCanceledNotificationTrigger trigger = trigger(
                notification -> !alreadyNotified.contains(notification.getMemberId()),
                (memberId, event) -> publishedMemberIds.add(memberId));

        trigger.onMeetingCanceled(event(List.of(3L, 7L, 9L)));

        assertThat(publishedMemberIds).containsExactlyInAnyOrder(3L, 9L);
    }

    /* 한 회원 처리 중 예외가 나도(저장 실패 등) 나머지 회원은 계속 처리되는지 검증한다. */
    @Test
    @DisplayName("한 회원 처리가 실패해도 나머지 회원은 계속 처리한다")
    void oneFailureDoesNotBlockOthers() {
        List<Long> publishedMemberIds = new ArrayList<>();
        MeetingCanceledNotificationTrigger trigger = trigger(
                notification -> {
                    if (notification.getMemberId().equals(7L)) {
                        throw new RuntimeException("저장 중 예상치 못한 오류");
                    }
                    return true;
                },
                (memberId, event) -> publishedMemberIds.add(memberId));

        assertThatCode(() -> trigger.onMeetingCanceled(event(List.of(3L, 7L, 9L))))
                .doesNotThrowAnyException();
        assertThat(publishedMemberIds).containsExactlyInAnyOrder(3L, 9L);
    }

    /* 발행되는 알림의 type·payload가 기대한 형태(제목 포함 메시지)인지 검증한다. */
    @Test
    @DisplayName("발행 이벤트는 MEETING_CANCELED 타입과 제목이 포함된 메시지를 담는다")
    void publishedEventHasExpectedShape() {
        List<NotificationEvent> published = new ArrayList<>();
        MeetingCanceledNotificationTrigger trigger = trigger(
                notification -> true,
                (memberId, event) -> published.add(event));

        trigger.onMeetingCanceled(event(List.of(7L)));

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("MEETING_CANCELED");
        assertThat(published.get(0).payload())
                .isInstanceOf(MeetingCanceledNotificationTrigger.MeetingCanceledPayload.class);
        var payload = (MeetingCanceledNotificationTrigger.MeetingCanceledPayload) published.get(0).payload();
        assertThat(payload.message()).isEqualTo("스프린트 회고 회의가 취소되었습니다.");
        assertThat(payload.meetingId()).isEqualTo(MEETING_ID);
    }

    private MeetingCanceledEvent event(List<Long> attendeeMemberIds) {
        return new MeetingCanceledEvent(MEETING_ID, COMPANY_ID, 3L, attendeeMemberIds, "스프린트 회고",
                LocalDateTime.of(2026, 8, 10, 14, 0), LocalDateTime.of(2026, 8, 9, 9, 0));
    }

    private MeetingCanceledNotificationTrigger trigger(java.util.function.Predicate<Notification> saveIfAbsent,
                                                        NotificationPublishPort publishPort) {
        NotificationRepository repository = saveIfAbsent::test;
        return new MeetingCanceledNotificationTrigger(repository, publishPort);
    }
}
