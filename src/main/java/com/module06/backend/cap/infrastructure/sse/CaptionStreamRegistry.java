package com.module06.backend.cap.infrastructure.sse;

import com.module06.backend.cap.application.port.out.CaptionStreamPort;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* comment.
    CAP-13 SSE의 실제 emitter 레지스트리 — 이 인스턴스에 연결된 자막 구독자를 회의별로 들고 있다.
    캡션·참석자 이벤트는 이 인스턴스가 직접 만든 것도, Redis pub/sub(CaptionStreamRedisListener)으로
    다른 인스턴스에서 온 것도 전부 여기 dispatchXxx로 들어와 로컬 emitter들에게만 전달된다 — 그래서
    인스턴스가 여러 대여도 "이 인스턴스에 붙어있는 클라이언트"에게만 정확히 나간다.

    참석자 수(connectedCount)는 Redis Hash(cap:captions:participants:{meetingId}, memberId → 연결 수)로
    인스턴스 간 공유한다. Set이 아니라 Hash인 이유 — 같은 사람이 다른 인스턴스에도 연결돼 있을 때, 이
    인스턴스의 연결 하나가 끊겼다고 그 사람 전체를 지우면 안 된다(연결 수를 세야 정확함). 필드값이
    0 이하가 되면 그때 지운다.
*/
@Component
public class CaptionStreamRegistry implements CaptionStreamPort {

    private static final Logger log = LoggerFactory.getLogger(CaptionStreamRegistry.class);
    private static final String PARTICIPANT_KEY_PREFIX = "cap:captions:participants:";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // meetingId -> 이 인스턴스에 연결된 구독자들. 회의당 동시 구독자가 아주 많지 않을 것으로 보고
    // CopyOnWriteArrayList로 단순하게 간다(쓰기=연결/해제는 드물고, 읽기=이벤트 전달은 순회뿐).
    private final Map<Long, CopyOnWriteArrayList<Subscriber>> emittersByMeeting = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "caption-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public CaptionStreamRegistry(MeetingReferenceRepository meetingReferenceRepository,
                                 StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // 새 SSE 연결을 등록한다. 타임아웃 0 = 서버 쪽에서 먼저 끊지 않음(네트워크 단절은 onError/onTimeout이 처리).
    @Override
    public SseEmitter subscribe(Long meetingId, Long memberId) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(memberId, emitter);
        // compute로 "생성 또는 추가"를 원자적으로 — computeIfAbsent+add 두 단계로 나누면 그 사이에
        // unregister가 끼어들어 방금 만든 빈 리스트를 지워버리는 경합이 생긴다.
        emittersByMeeting.compute(meetingId, (id, existing) -> {
            CopyOnWriteArrayList<Subscriber> subscribers = existing != null ? existing : new CopyOnWriteArrayList<>();
            subscribers.add(subscriber);
            return subscribers;
        });

        Runnable cleanup = () -> unregister(meetingId, subscriber);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        addParticipant(meetingId, memberId);
        publishParticipantSnapshot(meetingId);
        return emitter;
    }

    // remove와 "비었으면 맵에서 제거"를 원자적으로 — subscribe의 compute와 같은 맵 슬롯을 두고 경쟁하므로,
    // 별도 단계로 나누면 마지막 구독자 해제와 새 구독자 등록이 교차할 때 방금 등록된 구독자가 통째로
    // 유실될 수 있다(그 구독자는 이후 어떤 이벤트도 못 받음).
    private void unregister(Long meetingId, Subscriber subscriber) {
        emittersByMeeting.compute(meetingId, (id, subscribers) -> {
            if (subscribers == null) {
                return null;
            }
            subscribers.remove(subscriber);
            return subscribers.isEmpty() ? null : subscribers;
        });
        removeParticipant(meetingId, subscriber.memberId());
        publishParticipantSnapshot(meetingId);
    }

    // Redis pub/sub으로 받은 자막 조각들을 이 인스턴스에 붙은 이 회의 구독자에게만 전달한다.
    public void dispatchCaptions(Long meetingId, List<CaptionEvent> items) {
        List<Subscriber> subscribers = emittersByMeeting.get(meetingId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (CaptionEvent item : items) {
            broadcastToLocal(meetingId, subscribers, "caption", item);
        }
    }

    // Redis pub/sub으로 받은 참석자 스냅샷을 이 인스턴스에 붙은 이 회의 구독자에게만 전달한다.
    public void dispatchParticipant(Long meetingId, ParticipantEvent event) {
        List<Subscriber> subscribers = emittersByMeeting.get(meetingId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        broadcastToLocal(meetingId, subscribers, "participant", event);
    }

    private void sendHeartbeats() {
        long now = System.currentTimeMillis();
        emittersByMeeting.forEach((meetingId, subscribers) ->
                broadcastToLocal(meetingId, subscribers, "heartbeat", new HeartbeatEvent(now)));
    }

    // 죽은 emitter(전송 실패)는 그 자리에서 정리한다 — onError 콜백이 나중에 불릴 수도 있지만,
    // 다음 하트비트/이벤트 전송 때까지 죽은 연결로 계속 시도하지 않도록 즉시 제거한다.
    private void broadcastToLocal(Long meetingId, List<Subscriber> subscribers, String eventName, Object payload) {
        for (Subscriber subscriber : List.copyOf(subscribers)) {
            try {
                subscriber.emitter().send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                unregister(meetingId, subscriber);
            }
        }
    }

    // 연결 수를 1 올린다(HINCRBY, 원자적). 다른 인스턴스에 이미 연결돼 있어도 정확히 누적된다.
    private void addParticipant(Long meetingId, Long memberId) {
        try {
            redisTemplate.opsForHash().increment(participantKey(meetingId), memberId.toString(), 1);
        } catch (DataAccessException e) {
            log.warn("참석자 등록 실패(meetingId={}, memberId={}) — Redis 접근 오류, SSE 연결은 계속 진행", meetingId, memberId, e);
        }
    }

    // 연결 수를 1 내리고, 0 이하가 되면 필드 자체를 지운다 — 다른 인스턴스의 연결은 그대로 살아있다.
    private void removeParticipant(Long meetingId, Long memberId) {
        try {
            String key = participantKey(meetingId);
            String field = memberId.toString();
            long remaining = redisTemplate.opsForHash().increment(key, field, -1);
            if (remaining <= 0) {
                redisTemplate.opsForHash().delete(key, field);
            }
        } catch (DataAccessException e) {
            log.warn("참석자 해제 실패(meetingId={}, memberId={}) — Redis 접근 오류", meetingId, memberId, e);
        }
    }

    // connectedCount(Redis Hash 필드 수) + totalCount(회의 전체 참석자)를 묶어 참석자 채널로 발행한다.
    // 발행 자체가 이 인스턴스의 로컬 구독자에게도 그대로 되돌아와 dispatchParticipant로 전달된다.
    private void publishParticipantSnapshot(Long meetingId) {
        try {
            long connectedCount = redisTemplate.opsForHash().size(participantKey(meetingId));
            int totalCount = meetingReferenceRepository.countAttendees(meetingId);
            String json = objectMapper.writeValueAsString(
                    new ParticipantMessage(meetingId, (int) connectedCount, totalCount));
            redisTemplate.convertAndSend(CaptionStreamChannels.PARTICIPANT, json);
        } catch (DataAccessException | JacksonException e) {
            log.warn("참석자 스냅샷 발행 실패(meetingId={}) — Redis 접근 오류", meetingId, e);
        }
    }

    private String participantKey(Long meetingId) {
        return PARTICIPANT_KEY_PREFIX + meetingId;
    }

    private record Subscriber(Long memberId, SseEmitter emitter) {
    }

    /** Redis 캡션 채널 페이로드 — {@link CaptionStreamRedisListener}가 파싱해 이 회의 구독자에게만 나눠준다. */
    public record CaptionMessage(Long meetingId, List<CaptionEvent> items) {
    }

    /** 클라이언트로 나가는 caption 이벤트 데이터(스펙 필드명 그대로). */
    public record CaptionEvent(Long personId, String name, int startMs, String text) {
    }

    /** Redis 참석자 채널 페이로드. */
    public record ParticipantMessage(Long meetingId, int connectedCount, int totalCount) {
    }

    /** 클라이언트로 나가는 participant 이벤트 데이터(스펙 필드명 그대로). */
    public record ParticipantEvent(int connectedCount, int totalCount) {
    }

    /** 클라이언트로 나가는 heartbeat 이벤트 데이터(스펙 필드명 그대로: t). */
    public record HeartbeatEvent(long t) {
    }
}
