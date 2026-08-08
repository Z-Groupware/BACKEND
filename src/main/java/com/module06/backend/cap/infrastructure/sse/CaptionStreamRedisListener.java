package com.module06.backend.cap.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/* comment.
    Redis 캡션·참석자 채널 구독자. 어느 인스턴스가 발행했든(자기 자신 포함) 메시지를 받아
    CaptionStreamRegistry의 dispatchXxx로 넘겨 "이 인스턴스에 붙은 로컬 구독자"에게만 전달한다.
    이 빈 자체는 순수 리스너 로직이라 테스트에서도 안전하게 존재할 수 있다 — 실제로 Redis에 연결해
    구독을 시작하는 건 CaptionStreamRedisConfig의 컨테이너(cap.captions.sse.enabled 프로퍼티로 게이트).
*/
@Component
public class CaptionStreamRedisListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(CaptionStreamRedisListener.class);

    private final CaptionStreamRegistry registry;
    // 자동구성된 ObjectMapper 빈이 없어 로컬 인스턴스를 쓴다(CaptionStreamRegistry와 동일 이유).
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CaptionStreamRedisListener(CaptionStreamRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if (CaptionStreamChannels.CAPTION.equals(channel)) {
                CaptionStreamRegistry.CaptionMessage parsed =
                        objectMapper.readValue(body, CaptionStreamRegistry.CaptionMessage.class);
                registry.dispatchCaptions(parsed.meetingId(), parsed.items());
            } else if (CaptionStreamChannels.PARTICIPANT.equals(channel)) {
                CaptionStreamRegistry.ParticipantMessage parsed =
                        objectMapper.readValue(body, CaptionStreamRegistry.ParticipantMessage.class);
                registry.dispatchParticipant(parsed.meetingId(),
                        new CaptionStreamRegistry.ParticipantEvent(parsed.connectedCount(), parsed.totalCount()));
            }
        } catch (Exception e) {
            // 파싱 실패한 메시지 하나 때문에 리스너 스레드가 죽으면 이후 모든 회의의 실시간 전달이 끊긴다.
            log.warn("자막 스트림 메시지 처리 실패(channel={})", channel, e);
        }
    }
}
