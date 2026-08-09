package com.module06.backend.notification.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.module06.backend.notification.application.port.out.NotificationEvent;

/*
 * 알림 SSE 레지스트리의 구독 등록/해제·이벤트 전달을 검증한다. 이 인프라와 짝인 cap의
 * CaptionStreamRegistry는 지금까지 단위 테스트가 하나도 없었다(주석엔 "테스트로 커버"라고
 * 적혀 있지만 실제 파일이 없었음) — 이 파일이 그 공백을 새 코드에서는 반복하지 않는다.
 *
 * SseEmitter.send()는 실제 HTTP 응답에 연결되기 전(핸들러 미초기화)엔 예외를 던지지 않고 그냥
 * 큐에 쌓기만 하므로, "바이트가 실제로 나갔는지"는 여기서 검증하지 않는다 — 대신 구독/해제가
 * 내부 상태(emittersByMember)를 정확히 바꾸는지, dispatch가 구독자 유무와 무관하게 예외 없이
 * 동작하는지를 검증한다.
 *
 * 매 테스트마다 새 레지스트리를 만들면 heartbeat용 daemon 스레드가 인스턴스마다 하나씩 생기는데,
 * @PreDestroy는 스프링 컨텍스트가 관리할 때만 불리고 여기선 순수 new라 안 불린다 — 그래서
 * @AfterEach에서 직접 shutdown()을 호출해 정리한다(CodeRabbit 지적).
 */
@DisplayName("알림 SSE 레지스트리")
class NotificationStreamRegistryTest {

    private static final Long MEMBER_ID = 7L;

    private NotificationStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationStreamRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    /* 구독하면 내부 맵에 그 회원 앞으로 emitter가 하나 등록되는지 검증한다. */
    @Test
    @DisplayName("구독하면 회원 앞으로 emitter가 등록된다")
    void subscribeRegistersEmitter() throws Exception {
        SseEmitter emitter = registry.subscribe(MEMBER_ID);

        assertThat(emitter).isNotNull();
        assertThat(emittersFor(registry, MEMBER_ID)).hasSize(1);
    }

    /* 같은 회원이 여러 탭/기기로 구독하면(다중 연결) 전부 등록되는지 검증한다. */
    @Test
    @DisplayName("같은 회원이 여러 번 구독하면 emitter가 누적된다")
    void multipleSubscriptionsAccumulate() throws Exception {
        registry.subscribe(MEMBER_ID);
        registry.subscribe(MEMBER_ID);

        assertThat(emittersFor(registry, MEMBER_ID)).hasSize(2);
    }

    /* 연결이 끊기면(unregister) 등록이 해제되고, 마지막 하나가 빠지면 맵에서 그 회원 키 자체가
       사라지는지 검증한다. subscribe()가 onCompletion/onError/onTimeout에 등록하는 콜백은 실제
       Spring MVC 비동기 요청 처리기가 있어야 발동하므로(순수 POJO로 만든 emitter에 .complete()를
       불러도 콜백이 안 불림), 그 콜백이 실행하는 것과 동일한 private unregister를 리플렉션으로
       직접 호출해 로직 자체(원자적 제거 + 빈 리스트 정리)를 검증한다. */
    @Test
    @DisplayName("연결이 끊기면 등록이 해제된다")
    void unregisterRemovesEmitter() throws Exception {
        SseEmitter emitter = registry.subscribe(MEMBER_ID);

        invokeUnregister(registry, MEMBER_ID, emitter);

        assertThat(emittersMap(registry)).doesNotContainKey(MEMBER_ID);
    }

    /* 같은 회원의 emitter가 두 개일 때 하나만 해제하면, 키는 남고 나머지 하나만 남는지 검증한다. */
    @Test
    @DisplayName("여러 emitter 중 하나만 끊기면 나머지는 그대로 남는다")
    void unregisterOneKeepsOthers() throws Exception {
        SseEmitter first = registry.subscribe(MEMBER_ID);
        SseEmitter second = registry.subscribe(MEMBER_ID);

        invokeUnregister(registry, MEMBER_ID, first);

        assertThat(emittersFor(registry, MEMBER_ID)).containsExactly(second);
    }

    private void invokeUnregister(NotificationStreamRegistry registry, Long memberId, SseEmitter emitter)
            throws Exception {
        java.lang.reflect.Method method =
                NotificationStreamRegistry.class.getDeclaredMethod("unregister", Long.class, SseEmitter.class);
        method.setAccessible(true);
        method.invoke(registry, memberId, emitter);
    }

    /* 구독자가 없는 회원에게 dispatch해도 예외 없이 조용히 무시되는지 검증한다. */
    @Test
    @DisplayName("구독자가 없으면 dispatch는 조용히 무시된다")
    void dispatchToNobodyIsNoOp() {
        assertThatCode(() -> registry.dispatch(999L, new NotificationEvent("ACTION_ASSIGNED", "payload")))
                .doesNotThrowAnyException();
    }

    /* 구독자가 있는 회원에게 dispatch해도 예외가 나지 않는지 검증한다(핸들러 미초기화 emitter라
       실제 전송 여부는 못 보지만, 최소한 죽지 않아야 한다). */
    @Test
    @DisplayName("구독자가 있으면 dispatch해도 예외가 나지 않는다")
    void dispatchToSubscriberDoesNotThrow() {
        registry.subscribe(MEMBER_ID);

        assertThatCode(() -> registry.dispatch(MEMBER_ID, new NotificationEvent("ACTION_ASSIGNED", "payload")))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, java.util.List<SseEmitter>> emittersMap(NotificationStreamRegistry registry) throws Exception {
        Field field = NotificationStreamRegistry.class.getDeclaredField("emittersByMember");
        field.setAccessible(true);
        return (Map<Long, java.util.List<SseEmitter>>) field.get(registry);
    }

    private java.util.List<SseEmitter> emittersFor(NotificationStreamRegistry registry, Long memberId) throws Exception {
        return emittersMap(registry).get(memberId);
    }
}
