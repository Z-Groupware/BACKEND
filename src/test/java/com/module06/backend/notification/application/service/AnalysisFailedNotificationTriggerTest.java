package com.module06.backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.event.AnalysisFailedEvent;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * A 도메인의 AnalysisFailedEvent를 받아 개설자에게 저장 후 발행하는지, 이미 저장된
 * 경우 실시간 발행을 건너뛰는지, 저장·발행 실패가 예외로 밖에 나가지 않는지 검증한다.
 */
@DisplayName("분석 실패 알림 트리거")
class AnalysisFailedNotificationTriggerTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MEETING_ID = 500L;
    private static final Long HOST_ID = 3L;

    /* 개설자에게 저장 성공 시 실시간 발행까지 되는지 검증한다. */
    @Test
    @DisplayName("개설자에게 저장하고 발행한다")
    void savesAndPublishesToHost() {
        List<Long> savedMemberIds = new ArrayList<>();
        List<Long> publishedMemberIds = new ArrayList<>();
        AnalysisFailedNotificationTrigger trigger = trigger(
                notification -> { savedMemberIds.add(notification.getMemberId()); return true; },
                (memberId, event) -> publishedMemberIds.add(memberId));

        trigger.onAnalysisFailed(event());

        assertThat(savedMemberIds).containsExactly(HOST_ID);
        assertThat(publishedMemberIds).containsExactly(HOST_ID);
    }

    /* 저장(saveIfAbsent)이 false(이미 있던 알림)면 실시간 발행을 안 하는지 검증한다. */
    @Test
    @DisplayName("이미 저장된 알림은 실시간 발행을 건너뛴다")
    void skipsPublishForAlreadySaved() {
        List<Long> publishedMemberIds = new ArrayList<>();
        AnalysisFailedNotificationTrigger trigger = trigger(
                notification -> false,
                (memberId, event) -> publishedMemberIds.add(memberId));

        trigger.onAnalysisFailed(event());

        assertThat(publishedMemberIds).isEmpty();
    }

    /* 저장·발행 중 예외가 나도 밖으로 던지지 않는지 검증한다. */
    @Test
    @DisplayName("처리 실패가 예외로 밖에 나가지 않는다")
    void failureDoesNotThrow() {
        AnalysisFailedNotificationTrigger trigger = trigger(
                notification -> { throw new RuntimeException("저장 중 예상치 못한 오류"); },
                (memberId, event) -> { });

        assertThatCode(() -> trigger.onAnalysisFailed(event())).doesNotThrowAnyException();
    }

    /* 발행되는 알림의 type·payload가 기대한 형태(제목·에러코드 포함 메시지)인지 검증한다. */
    @Test
    @DisplayName("발행 이벤트는 ANALYSIS_FAILED 타입과 제목이 포함된 메시지를 담는다")
    void publishedEventHasExpectedShape() {
        List<NotificationEvent> published = new ArrayList<>();
        AnalysisFailedNotificationTrigger trigger = trigger(
                notification -> true,
                (memberId, event) -> published.add(event));

        trigger.onAnalysisFailed(event());

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("ANALYSIS_FAILED");
        assertThat(published.get(0).payload())
                .isInstanceOf(AnalysisFailedNotificationTrigger.AnalysisFailedPayload.class);
        var payload = (AnalysisFailedNotificationTrigger.AnalysisFailedPayload) published.get(0).payload();
        assertThat(payload.message()).isEqualTo("스프린트 회고 회의 요약에 실패했습니다.");
        assertThat(payload.meetingId()).isEqualTo(MEETING_ID);
        assertThat(payload.errorCode()).isEqualTo("L4_TIMEOUT");
    }

    private AnalysisFailedEvent event() {
        return new AnalysisFailedEvent(COMPANY_ID, MEETING_ID, HOST_ID, "스프린트 회고", "L4_TIMEOUT");
    }

    private AnalysisFailedNotificationTrigger trigger(
            java.util.function.Predicate<Notification> saveIfAbsent,
            NotificationPublishPort publishPort
    ) {
        NotificationRepository repository = saveIfAbsent::test;
        return new AnalysisFailedNotificationTrigger(repository, publishPort);
    }
}
