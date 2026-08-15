package com.module06.backend.global.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 고정 창(fixed window) 카운터. Redis 를 쓰는 이유는 인스턴스가 늘어도 카운터가 하나여야 하기
 * 때문이다 — 애플리케이션 메모리에 두면 인스턴스 수만큼 한도가 곱해진다.
 *
 * <p>Lua 로 묶는 이유 — {@code INCR} 과 {@code PEXPIRE} 가 따로 나가면 그 사이에 프로세스가
 * 죽었을 때 TTL 없는 키가 남는다. 그 키는 영원히 남아 해당 IP·계정을 <b>영구히</b> 잠근다.
 * 갱신표 저장소({@code RedisRefreshTokenStore})가 같은 이유로 Lua 를 쓴다.
 *
 * <p><b>고정 창의 한계를 적어 둔다</b> — 창 경계에서는 짧은 순간 한도의 두 배까지 통과할 수 있다
 * (창 끝에 20회 + 다음 창 시작에 20회). 무차별 대입 방어에는 문제가 되지 않는다: 공격자가 얻는
 * 것은 한 번의 2배 버스트일 뿐이고 시간당 총량은 그대로다. 정밀한 평활화가 필요해지면 그때
 * 슬라이딩 창으로 바꾼다 — 지금 필요한 것은 "무제한이 아닌 상태"다.
 *
 * <p><b>Redis 가 죽으면 통과시킨다(fail-open)</b>. 판단이 갈리는 자리라 근거를 남긴다:
 * 이 제한은 인증이 아니라 남용 억제이고, 뒤에 진짜 인증(비밀번호·갱신표 검증)이 그대로 있다.
 * fail-closed 로 두면 Redis 장애가 곧 전체 로그인 불가가 된다 — 훨씬 흔하고 확실한 피해다.
 * 대신 그 상태를 로그로 남겨 조용히 뚫려 있지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY = "ratelimit:%s:%s";

    /**
     * KEYS[1]=카운터 키, ARGV[1]=창 길이(ms). 반환 {@code {횟수, 남은 TTL(ms)}}.
     *
     * <p>TTL 이 음수면(만료 없는 키가 어떻게든 생겼다면) 다시 걸어 준다 — 그 방어가 없으면
     * 그 키가 영구 차단이 된다.
     */
    private static final RedisScript<List> RECORD = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    /** 계상하지 않고 읽기만. 키가 없으면 {@code {0, -2}} 로 온다. */
    private static final RedisScript<List> PEEK = RedisScript.of("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;

    @Override
    public Decision record(RateLimitPolicy policy, String subject) {
        // RECORD 는 이번 시도를 이미 세었다.
        return evaluate(RECORD, policy, subject, 0);
    }

    @Override
    public Decision peek(RateLimitPolicy policy, String subject) {
        // PEEK 은 세지 않았으므로 "세었다고 치고" 같은 기준으로 본다. 이 보정이 없으면
        // limit 번 실패한 계정이 한 번 더 통과한다 — 마지막 한 번이 대입의 정답일 수 있다.
        return evaluate(PEEK, policy, subject, 1);
    }

    /**
     * @param pending 아직 세지 않은 이번 시도의 몫. {@code record} 는 0, {@code peek} 은 1이다.
     *                두 경로가 같은 뜻("limit 번까지 허용")을 갖게 맞추는 값이다.
     */
    private Decision evaluate(RedisScript<List> script, RateLimitPolicy policy, String subject, int pending) {
        try {
            List<?> result = redis.execute(script,
                    List.of(key(policy, subject)),
                    String.valueOf(policy.window().toMillis()));

            if (result == null || result.size() < 2) {
                return failOpen(policy, "예상과 다른 응답: " + result);
            }

            long count = toLong(result.get(0)) + pending;
            if (count <= policy.limit()) {
                return Decision.pass();
            }
            return new Decision(false, retryAfter(toLong(result.get(1)), policy));
        } catch (RuntimeException ex) {
            return failOpen(policy, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * 남은 TTL 을 초로 올림한다. 0 초를 주면 클라이언트가 즉시 재시도해 의미가 없으므로 최소 1초다.
     * TTL 이 음수(키 없음·만료 없음)면 창 길이를 그대로 안내한다.
     */
    private Duration retryAfter(long ttlMillis, RateLimitPolicy policy) {
        if (ttlMillis < 0) {
            return policy.window();
        }
        return Duration.ofSeconds(Math.max(1, (ttlMillis + 999) / 1000));
    }

    private Decision failOpen(RateLimitPolicy policy, String reason) {
        // WARN 이다 — 제한이 꺼진 채로 서비스가 도는 상태이고, 오래 지속되면 사람이 봐야 한다.
        log.warn("RATE_LIMIT 판정 실패 — 통과시킨다(fail-open). policy={} 원인={}", policy.name(), reason);
        return Decision.pass();
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String key(RateLimitPolicy policy, String subject) {
        return KEY.formatted(policy.name(), subject);
    }
}
