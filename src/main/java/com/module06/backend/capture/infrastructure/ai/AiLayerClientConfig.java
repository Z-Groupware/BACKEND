package com.module06.backend.capture.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/*
 * AI EC2 호출용 RestClient 를 만든다.
 *
 * 토큰을 **기본 헤더로 한 번만** 건다. 호출부마다 붙이면 언젠가 한 곳이 빠지고, 그 경로만
 * 401 로 죽는다 — 그런 종류의 버그는 그 계층이 실제로 돌 때까지 드러나지 않는다.
 *
 * 설정 검증은 {@link AiLayerProperties} 생성 시점에 끝난다(비어 있으면 부팅 실패). 그래서
 * 여기서는 널 폴백을 두지 않는다 — 폴백이 있으면 "설정을 안 했는데 뜨는" 상태가 되고,
 * 그건 매 호출 401 로 런타임에 가서야 드러난다.
 */
@Configuration
@EnableConfigurationProperties(AiLayerProperties.class)
public class AiLayerClientConfig {

    @Bean
    public RestClient aiRestClient(AiLayerProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        // 계층 하나가 회의 전체를 읽고 LLM 을 부른다. 초 단위로 잡으면 정상 동작이
        // 타임아웃으로 실패하고, 재시도가 같은 토큰을 다시 태운다.
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .requestFactory(factory)
                .build();
    }
}
