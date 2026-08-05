package com.module06.backend.cap.infrastructure.heartbeat;

import com.module06.backend.cap.application.port.out.CaptureHeartbeatPort;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/* comment.
    cap:heartbeat:{meetingId} 키를 30초 TTL로 SETEX — 존재하면 살아있는 것으로 판정.
    Redis 장애 시 fail-safe: isAlive()는 true(=살아있다고 간주)를 반환해 canTakeover가
    false로 고정되게 한다(이어받기 버튼을 숨기는 쪽이 안전 — 엉뚱한 사람이 채가지 않음).
*/
@Component
public class RedisCaptureHeartbeatAdapter implements CaptureHeartbeatPort {

    private static final String KEY_PREFIX = "cap:heartbeat:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public RedisCaptureHeartbeatAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Redis 키를 30초 TTL로 다시 세팅 (기존 값 덮어씀)
    @Override
    public void refresh(Long meetingId) {
        redisTemplate.opsForValue().set(key(meetingId), "alive", TTL);
    }

    // 키가 아직 안 지워졌으면(TTL 안 끝났으면) 살아있는 것
    @Override
    public boolean isAlive(Long meetingId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(meetingId)));
        } catch (DataAccessException e) {
            return true;
        }
    }

    // 회의별 Redis 키 이름 조립
    private String key(Long meetingId) {
        return KEY_PREFIX + meetingId;
    }
}
