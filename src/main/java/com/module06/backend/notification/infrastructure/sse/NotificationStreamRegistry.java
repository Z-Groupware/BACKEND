package com.module06.backend.notification.infrastructure.sse;

import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationStreamPort;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* comment.
    알림 SSE의 실제 emitter 레지스트리 — 이 인스턴스에 연결된 구독자를 회원별로 들고 있다. cap의
    CaptionStreamRegistry와 동일 패턴(회의 대신 회원 단위)이다. 다른 도메인이 발행한 이벤트는 이
    인스턴스가 직접 만든 것도, Redis pub/sub(NotificationRedisListener)으로 다른 인스턴스에서 온
    것도 전부 dispatch로 들어와 로컬 emitter들에게만 전달된다 — 그래서 인스턴스가 여러 대여도 "이
    인스턴스에 붙어있는 클라이언트"에게만 정확히 나간다.

    같은 회원이 여러 탭/기기로 동시에 붙어있을 수 있어 회원당 emitter를 리스트로 들고, 전부에게
    복사해서 보낸다(참석자 수 같은 카운트 개념은 없다 — 개인 알림이라 "몇 명 연결됐는지"를 누구에게도
    보여줄 필요가 없다. cap과의 차이점).
*/
@Component
public class NotificationStreamRegistry implements NotificationStreamPort {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    // memberId -> 이 인스턴스에 연결된 emitter들(같은 회원의 여러 탭/기기). 연결/해제는 드물고 전달은
    // 순회뿐이라 CopyOnWriteArrayList로 단순하게 간다(cap과 동일 근거).
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByMember = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notification-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationStreamRegistry() {
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // 새 SSE 연결을 등록한다. 타임아웃 0 = 서버 쪽에서 먼저 끊지 않음(네트워크 단절은 onError/onTimeout이 처리).
    @Override
    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(0L);
        // compute로 "생성 또는 추가"를 원자적으로 — computeIfAbsent+add 두 단계로 나누면 그 사이에
        // unregister가 끼어들어 방금 만든 빈 리스트를 지워버리는 경합이 생긴다(cap과 동일 근거).
        emittersByMember.compute(memberId, (id, existing) -> {
            CopyOnWriteArrayList<SseEmitter> emitters = existing != null ? existing : new CopyOnWriteArrayList<>();
            emitters.add(emitter);
            return emitters;
        });

        Runnable cleanup = () -> unregister(memberId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    // remove와 "비었으면 맵에서 제거"를 원자적으로 처리한다(cap의 unregister와 동일 근거).
    private void unregister(Long memberId, SseEmitter emitter) {
        emittersByMember.compute(memberId, (id, emitters) -> {
            if (emitters == null) {
                return null;
            }
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    // Redis pub/sub으로 받은(또는 이 인스턴스가 직접 발행한) 이벤트를 이 인스턴스에 붙은 해당 회원
    // 구독자에게만 전달한다.
    public void dispatch(Long memberId, NotificationEvent event) {
        List<SseEmitter> emitters = emittersByMember.get(memberId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        broadcastToLocal(memberId, emitters, "notification", event);
    }

    private void sendHeartbeats() {
        long now = System.currentTimeMillis();
        emittersByMember.forEach((memberId, emitters) ->
                broadcastToLocal(memberId, emitters, "heartbeat", new HeartbeatEvent(now)));
    }

    // 죽은 emitter(전송 실패)는 그 자리에서 정리한다(cap과 동일 근거).
    private void broadcastToLocal(Long memberId, List<SseEmitter> emitters, String eventName, Object payload) {
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                unregister(memberId, emitter);
            }
        }
    }

    /** Redis 알림 채널 페이로드 — {@link NotificationRedisListener}가 파싱해 이 회원 구독자에게만 넘긴다. */
    public record NotificationMessage(Long memberId, NotificationEvent event) {
    }

    /** 클라이언트로 나가는 heartbeat 이벤트 데이터(cap과 동일 필드명: t). */
    public record HeartbeatEvent(long t) {
    }
}
