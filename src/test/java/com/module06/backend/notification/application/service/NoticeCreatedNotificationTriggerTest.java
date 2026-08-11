package com.module06.backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.module06.backend.notice.application.event.NoticeCreatedEvent;
import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;
import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;

/*
 * 공지 등록 이벤트가 커밋 이후 같은 회사 활성 회원 전체에게 배너 SSE로 발행되는지 검증한다.
 */
@DisplayName("공지 등록 SSE 알림 트리거")
class NoticeCreatedNotificationTriggerTest {

    /* 테스트 공지가 속한 회사 식별자다. */
    private static final Long COMPANY_ID = 10L;

    /* payload와 로그 계약에서 공통으로 사용하는 공지 식별자다. */
    private static final Long NOTICE_ID = 31L;

    /* 공지 작성 트랜잭션이 커밋된 뒤에만 리스너가 실행되도록 선언했는지 검증한다. */
    @Test
    @DisplayName("공지 등록 이벤트를 AFTER_COMMIT 단계에서 구독한다")
    void listensAfterCommit() throws NoSuchMethodException {
        /* 공지 이벤트 처리 메서드에 선언된 트랜잭션 이벤트 메타데이터를 읽는다. */
        Method listenerMethod = NoticeCreatedNotificationTrigger.class
                .getMethod("onNoticeCreated", NoticeCreatedEvent.class);
        TransactionalEventListener listener = listenerMethod.getAnnotation(TransactionalEventListener.class);

        /* 롤백된 공지의 배너가 발행되지 않도록 실행 단계가 AFTER_COMMIT이어야 한다. */
        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    /* 같은 회사의 활성 회원 조회 결과 전체에게 이벤트를 발행하는지 검증한다. */
    @Test
    @DisplayName("같은 회사 활성 회원 전체에게 NOTICE_CREATED를 발행한다")
    void publishesToAllActiveCompanyMembers() {
        /* 회원별 발행 결과를 순서대로 기록할 목록을 준비한다. */
        List<Long> publishedMemberIds = new ArrayList<>();
        List<NotificationEvent> publishedEvents = new ArrayList<>();
        NoticeCreatedNotificationTrigger trigger = trigger(
                companyId -> {
                    assertThat(companyId).isEqualTo(COMPANY_ID);
                    return List.of(3L, 7L, 9L);
                },
                (memberId, event) -> {
                    publishedMemberIds.add(memberId);
                    publishedEvents.add(event);
                }
        );

        /* 회사 10의 공지 등록 이벤트를 처리한다. */
        trigger.onNoticeCreated(event());

        /* 활성 회원 세 명에게만 같은 공지 이벤트가 발행돼야 한다. */
        assertThat(publishedMemberIds).containsExactly(3L, 7L, 9L);
        assertThat(publishedEvents).hasSize(3);
        assertThat(publishedEvents).extracting(NotificationEvent::type).containsOnly("NOTICE_CREATED");

        /* 프론트 배너에 필요한 공지 식별자·제목·메시지가 payload에 포함돼야 한다. */
        assertThat(publishedEvents.get(0).payload())
                .isInstanceOf(NoticeCreatedNotificationTrigger.NoticeCreatedPayload.class);
        var payload = (NoticeCreatedNotificationTrigger.NoticeCreatedPayload) publishedEvents.get(0).payload();
        assertThat(payload.noticeId()).isEqualTo(NOTICE_ID);
        assertThat(payload.title()).isEqualTo("사내 워크숍 안내");
        assertThat(payload.message()).isEqualTo("사내 워크숍 안내 공지사항이 등록되었습니다.");
    }

    /* 특정 회원의 SSE 실패가 나머지 회사 회원의 알림을 막지 않는지 검증한다. */
    @Test
    @DisplayName("한 회원의 발행 실패 후에도 나머지 회원을 계속 처리한다")
    void isolatesOneMemberPublishFailure() {
        /* 7번 회원만 실패시키고 성공적으로 발행된 회원을 기록한다. */
        List<Long> publishedMemberIds = new ArrayList<>();
        NoticeCreatedNotificationTrigger trigger = trigger(
                companyId -> List.of(3L, 7L, 9L),
                (memberId, event) -> {
                    if (memberId.equals(7L)) {
                        throw new RuntimeException("Redis 발행 실패");
                    }
                    publishedMemberIds.add(memberId);
                }
        );

        /* 한 회원 실패가 리스너 밖으로 전파되지 않아야 한다. */
        assertThatCode(() -> trigger.onNoticeCreated(event())).doesNotThrowAnyException();

        /* 실패한 회원 앞뒤의 회원은 정상적으로 처리돼야 한다. */
        assertThat(publishedMemberIds).containsExactly(3L, 9L);
    }

    /* 활성 회원 조회 실패가 이미 커밋된 공지 등록 요청에 영향을 주지 않는지 검증한다. */
    @Test
    @DisplayName("회사 회원 조회 실패는 로그로 남기고 발행하지 않는다")
    void recipientQueryFailureDoesNotEscape() {
        /* 조회 시 예외를 내고 발행이 호출되면 테스트를 실패시키는 대역을 준비한다. */
        NoticeCreatedNotificationTrigger trigger = trigger(
                companyId -> {
                    throw new RuntimeException("회원 조회 실패");
                },
                (memberId, event) -> {
                    throw new AssertionError("회원 조회 실패 뒤에는 SSE를 발행하면 안 됩니다.");
                }
        );

        /* AFTER_COMMIT 리스너는 조회 실패를 밖으로 전파하지 않고 종료해야 한다. */
        assertThatCode(() -> trigger.onNoticeCreated(event())).doesNotThrowAnyException();
    }

    /* 모든 테스트에서 같은 공지 등록 이벤트를 생성한다. */
    private NoticeCreatedEvent event() {
        /* 회사 10에 생성된 공지 31의 표시 정보를 반환한다. */
        return new NoticeCreatedEvent(NOTICE_ID, COMPANY_ID, "사내 워크숍 안내");
    }

    /* 함수형 대역으로 공지 알림 트리거를 간결하게 조립한다. */
    private NoticeCreatedNotificationTrigger trigger(
            CompanyMemberQueryPort companyMemberQueryPort,
            NotificationPublishPort notificationPublishPort
    ) {
        /* 테스트별 조회·발행 동작을 주입한 트리거를 반환한다. */
        return new NoticeCreatedNotificationTrigger(companyMemberQueryPort, notificationPublishPort);
    }
}
