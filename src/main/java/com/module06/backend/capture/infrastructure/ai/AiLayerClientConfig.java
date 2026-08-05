package com.module06.backend.capture.infrastructure.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/*
 * AI EC2 호출용 RestClient 를 만든다.
 *
 * 토큰을 **기본 헤더로 한 번만** 건다. 호출부마다 붙이면 언젠가 한 곳이 빠지고, 그 경로만
 * 401 로 죽는다 — 그런 종류의 버그는 그 계층이 실제로 돌 때까지 드러나지 않는다.
 *
 * 설정이 비어 있어도 부팅은 시킨다. 이 저장소에는 분석과 무관한 도메인이 여럿 있고,
 * AI 설정이 없다고 전체가 못 뜨면 로컬 개발이 막힌다. 대신 경고를 남기고, 실제 호출은
 * 401 로 실패하며 그 실패가 analysis_layer 에 기록된다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiLayerProperties.class)
public class AiLayerClientConfig {

    @Bean
    public RestClient aiRestClient(AiLayerProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("AI 계층 설정이 비어 있다(ai.base-url · ai.internal-token). "
                    + "분석 계층 호출은 실패한다 — 로컬에서 분석을 돌리려면 채울 것.");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) durationOrDefault(properties.connectTimeout(), 5_000));
        // 계층 하나가 회의 전체를 읽고 LLM 을 부른다. 초 단위로 잡으면 정상 동작이
        // 타임아웃으로 실패하고, 재시도가 같은 토큰을 다시 태운다.
        factory.setReadTimeout((int) durationOrDefault(properties.readTimeout(), 180_000));

        return RestClient.builder()
                .baseUrl(properties.baseUrl() != null ? properties.baseUrl() : "http://localhost:8000")
                .defaultHeader("X-Internal-Token", properties.internalToken() != null
                        ? properties.internalToken() : "")
                .requestFactory(factory)
                .build();
    }

    private long durationOrDefault(Duration duration, long fallbackMillis) {
        return duration != null ? duration.toMillis() : fallbackMillis;
    }
}
