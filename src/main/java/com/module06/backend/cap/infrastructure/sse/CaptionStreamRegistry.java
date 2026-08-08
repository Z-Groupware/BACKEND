package com.module06.backend.cap.infrastructure.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.module06.backend.cap.application.port.out.CaptionStreamPort;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    CAP-13 SSE의 실제 emitter 레지스트리 — 이 인스턴스에 연결된 자막 구독자를 회의별로 들고 있다.
    캡션·참석자 이벤트는 이 인스턴스가 직접 만든 것도, Redis pub/sub(CaptionStreamRedisListener)으로
    다른 인스턴스에서 온 것도 전부 여기 dispatchXxx로 들어와 로컬 emitter들에게만 전달된다 — 그래서
    인스턴스가 여러 대여도 "이 인스턴스에 붙어있는 클라이언트"에게만 정확히 나간다.

    참석자 수(connectedCount)는 Redis Set(cap:captions:participants:{meetingId})으로 인스턴스 간
    공유한다 — 로컬 맵만 보면 다른 인스턴스에 붙은 참석자를 못 세기 때문. 같은 사람이 탭을 여러 개 열면
    Set엔 한 번만 잡혀 중복 집계되지 않는다(반대로 그 사람이 tab 하나만 닫아도 이 로컬 카운트가 0이 되기
    전까진 Redis에서 안 지운다 — 아래 disconnect 처리 참고).
*/
@Component
public class CaptionStreamRegistry implements CaptionStreamPort {

    private static final Logger log = LoggerFactory.getLogger(CaptionStreamRegistry.class);
    private static final String PARTICIPANT_KEY_PREFIX = "cap:captions:participants:";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final StringRedisTemplate redisTemplate;
    // 이 프로젝트엔 자동구성된 ObjectMapper 빈이 없다 — 페이로드가 Long/int/String뿐인 단순 record라
    // JSR310 등 추가 모듈 없이 로컬 인스턴스로 충분하다(스프링 빈 주입에 기대지 않는다).
    private final ObjectMapper objectMapper = new ObjectMapper();

    // meetingId -> 이 인스턴스에 연결된 구독자들. 회의당 동시 구독자가 아주 많지 않을 것으로 보고
    // CopyOnWriteArrayList로 단순하게 간다(쓰기=연결/해제는 드물고, 읽기=이벤트 전달은 순회뿐).
    private final Map<Long, CopyOnWriteArrayList<Subscriber>> emittersByMeeting = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "caption-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public CaptionStreamRegistry(MeetingReferenceRepository meetingReferenceRepository,
                                 StringRedisTemplate redisTemplate) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.redisTemplate = redisTemplate;
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // 새 SSE 연결을 등록한다. 타임아웃 0 = 서버 쪽에서 먼저 끊지 않음(네트워크 단절은 onError/onTimeout이 처리).
    @Override
    public SseEmitter subscribe(Long meetingId, Long memberId) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(memberId, emitter);
        emittersByMeeting.computeIfAbsent(meetingId, id -> new CopyOnWriteArrayList<>()).add(subscriber);

        Runnable cleanup = () -> unregister(meetingId, subscriber);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        addParticipant(meetingId, memberId);
        publishParticipantSnapshot(meetingId);
        return emitter;
    }

    private void unregister(Long meetingId, Subscriber subscriber) {
        CopyOnWriteArrayList<Subscriber> subscribers = emittersByMeeting.get(meetingId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(subscriber);
        if (subscribers.isEmpty()) {
            emittersByMeeting.remove(meetingId, subscribers);
        }
        // 같은 사람이 이 회의에 다른 탭(로컬 emitter)으로 아직 붙어있으면 Redis 참석자 집합에서 빼지 않는다.
        boolean stillConnectedLocally = subscribers.stream().anyMatch(s -> s.memberId().equals(subscriber.memberId()));
        if (!stillConnectedLocally) {
            removeParticipant(meetingId, subscriber.memberId());
            publishParticipantSnapshot(meetingId);
        }
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

    private void addParticipant(Long meetingId, Long memberId) {
        try {
            redisTemplate.opsForSet().add(participantKey(meetingId), memberId.toString());
        } catch (DataAccessException e) {
            log.warn("참석자 등록 실패(meetingId={}, memberId={}) — Redis 접근 오류, SSE 연결은 계속 진행", meetingId, memberId, e);
        }
    }

    private void removeParticipant(Long meetingId, Long memberId) {
        try {
            redisTemplate.opsForSet().remove(participantKey(meetingId), memberId.toString());
        } catch (DataAccessException e) {
            log.warn("참석자 해제 실패(meetingId={}, memberId={}) — Redis 접근 오류", meetingId, memberId, e);
        }
    }

    // connectedCount(Redis Set 크기) + totalCount(회의 전체 참석자)를 묶어 참석자 채널로 발행한다.
    // 발행 자체가 이 인스턴스의 로컬 구독자에게도 그대로 되돌아와 dispatchParticipant로 전달된다.
    private void publishParticipantSnapshot(Long meetingId) {
        try {
            long connectedCount = redisTemplate.opsForSet().size(participantKey(meetingId));
            int totalCount = meetingReferenceRepository.countAttendees(meetingId);
            String json = objectMapper.writeValueAsString(
                    new ParticipantMessage(meetingId, (int) connectedCount, totalCount));
            redisTemplate.convertAndSend(CaptionStreamChannels.PARTICIPANT, json);
        } catch (DataAccessException | JsonProcessingException e) {
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
