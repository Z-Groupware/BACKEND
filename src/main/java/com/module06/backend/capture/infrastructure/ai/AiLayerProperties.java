package com.module06.backend.capture.infrastructure.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
 * AI EC2 접속 설정이다.
 *
 * baseUrl 은 인스턴스 간 사설 IP다(localhost 가 아니다). 보안그룹이 Spring EC2 에서만
 * 인바운드를 허용하지만, internalToken 은 그것과 **별도로** 둔다 — 같은 VPC 안의 다른
 * 인스턴스가 뚫렸을 때 방어선이 하나도 없는 상태가 되지 않게.
 *
 * 타임아웃이 길다. 계층 하나가 회의 전체 발화를 읽고 LLM 을 부르므로 초 단위가 아니라
 * 분 단위다. 짧게 잡으면 정상 동작이 타임아웃으로 실패하고, 재시도가 같은 토큰을 다시 태운다.
 */
@ConfigurationProperties(prefix = "ai")
public record AiLayerProperties(
        String baseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiLayerProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofMinutes(3);
    }

    /*
     * 토큰이 비어 있으면 부팅에서 걸러야 한다. 비운 채로 뜨면 매 호출이 401 로 실패하는데,
     * 그건 런타임에 가서야 드러나고 그때는 이미 잡이 돌고 있다.
     */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && internalToken != null && !internalToken.isBlank();
    }
}
