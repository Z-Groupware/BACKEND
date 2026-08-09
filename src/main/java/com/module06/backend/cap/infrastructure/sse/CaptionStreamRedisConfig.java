package com.module06.backend.cap.infrastructure.sse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/* comment.
    CAP-13 SSE 브로드캐스트용 Redis pub/sub 구독을 실제로 시작하는 곳 — RedisMessageListenerContainer는
    빈이 뜨자마자(지연 연결 아님) Redis에 연결해 구독을 건다. CI엔 Redis 서비스가 없어서 이 빈을 그냥
    두면 cap과 무관한 도메인 테스트까지 포함해 전체 @SpringBootTest가 이 연결 시도에 걸린다.

    ⚠️ @Profile("!test")로 빼려 했으나 이 프로젝트는 테스트에서 실제 "test" 프로파일을 활성화하지 않고
    (src/test/resources/application.yaml을 기본 프로파일로 덮어쓰는 방식) 그래서 안 먹혔다 — 대신
    프로퍼티 기반 조건으로 뺀다. src/test/resources/application.yaml에 cap.captions.sse.enabled: false를
    명시해 테스트에서만 끈다(운영/로컬은 matchIfMissing=true라 별도 설정 없이 켜짐).

    ⚠️ 결과적으로 "Redis pub/sub이 실제로 붙어서 동작하는지"는 자동 테스트로 검증되지 않는다(CAP-13 PR
    논의에서 합의된 트레이드오프) — 로직(CaptionStreamRegistry·CaptionStreamRedisListener)은 순수
    단위 테스트로 커버하고, 실제 배선은 수동 확인 또는 후속으로 CI에 Redis 서비스가 추가되면 그때 커버한다.
*/
@Configuration
@ConditionalOnProperty(prefix = "cap.captions.sse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CaptionStreamRedisConfig {

    @Bean
    public RedisMessageListenerContainer captionStreamListenerContainer(
            RedisConnectionFactory connectionFactory, CaptionStreamRedisListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(CaptionStreamChannels.CAPTION));
        container.addMessageListener(listener, new ChannelTopic(CaptionStreamChannels.PARTICIPANT));
        return container;
    }
}
