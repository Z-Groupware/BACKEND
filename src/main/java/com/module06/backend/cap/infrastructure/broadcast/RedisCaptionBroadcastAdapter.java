package com.module06.backend.cap.infrastructure.broadcast;

import com.module06.backend.cap.application.port.out.CaptionBroadcastPort;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.MemberReferenceRepository;
import com.module06.backend.cap.infrastructure.sse.CaptionStreamChannels;
import com.module06.backend.cap.infrastructure.sse.CaptionStreamRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* comment.
    CaptionBroadcastPort의 실제 구현 — CAP-11이 저장한 새 자막을 Redis 캡션 채널로 발행한다.
    이 인스턴스를 포함한 모든 인스턴스의 CaptionStreamRedisListener가 이 메시지를 받아 각자 로컬
    구독자에게 나눠준다(CaptionStreamRegistry 참고). 발신자 이름은 여기서 한 번만 배치 조회해 싣는다 —
    클라이언트가 매 이벤트마다 이름을 따로 조회할 필요가 없게.
*/
@Component
public class RedisCaptionBroadcastAdapter implements CaptionBroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(RedisCaptionBroadcastAdapter.class);

    private final MemberReferenceRepository memberReferenceRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCaptionBroadcastAdapter(MemberReferenceRepository memberReferenceRepository,
                                        StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.memberReferenceRepository = memberReferenceRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(Long meetingId, List<CaptionChunk> newChunks) {
        if (newChunks.isEmpty()) {
            return;
        }
        Map<Long, String> namesByMemberId = lookupNames(newChunks);
        List<CaptionStreamRegistry.CaptionEvent> items = newChunks.stream()
                .map(chunk -> new CaptionStreamRegistry.CaptionEvent(
                        chunk.getMemberId(),
                        namesByMemberId.getOrDefault(chunk.getMemberId(), ""),
                        chunk.getStartOffsetMs(),
                        chunk.getText()))
                .toList();
        publish(new CaptionStreamRegistry.CaptionMessage(meetingId, items));
    }

    // best-effort — 이름 조회 실패(DB 접근 오류 등)로 브로드캐스트 전체를 죽이지 않는다. 저장(CAP-11)은
    // 이미 끝났으므로, 여기서 예외가 새면 이미 성공한 요청이 500으로 뒤집힌다. 실패 시 빈 이름으로 진행.
    private Map<Long, String> lookupNames(List<CaptionChunk> chunks) {
        List<Long> memberIds = chunks.stream().map(CaptionChunk::getMemberId).distinct().toList();
        try {
            return memberReferenceRepository.findNames(memberIds).stream()
                    .collect(Collectors.toMap(
                            MemberReferenceRepository.MemberName::memberId,
                            MemberReferenceRepository.MemberName::name));
        } catch (RuntimeException e) {
            log.warn("발신자 이름 조회 실패 — 빈 이름으로 브로드캐스트 계속 진행. memberIds={}", memberIds, e);
            return Map.of();
        }
    }

    // 브로드캐스트는 best-effort — Redis 장애가 자막 저장(CAP-11) 자체를 실패시키면 안 되므로 여기서 삼킨다.
    private void publish(CaptionStreamRegistry.CaptionMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CaptionStreamChannels.CAPTION, json);
        } catch (DataAccessException | JacksonException e) {
            log.warn("자막 브로드캐스트 발행 실패(meetingId={}) — Redis 접근 오류, 저장은 이미 완료됨", message.meetingId(), e);
        }
    }
}
