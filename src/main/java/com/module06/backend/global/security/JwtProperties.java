package com.module06.backend.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @param refreshAbsoluteMax 최초 로그인 이후 갱신표를 이어붙일 수 있는 총 기간.
 *                           {@code refreshTtl*} 은 표 한 장의 수명이고 재발급마다 새로 계산되므로,
 *                           이 상한이 없으면 갱신을 반복하는 것만으로 세션이 영원히 산다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtlDefault,
        Duration refreshTtlExtended,
        Duration refreshAbsoluteMax
) {
}
