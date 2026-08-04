package com.module06.backend.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
 * application.yaml 의 jwt.* 를 담는다.
 *
 * secret 은 application-secret.yml 에서 주입되며 최소 32바이트여야 한다(HS256 요구).
 * 짧으면 Keys.hmacShaKeyFor 가 즉시 예외를 던지므로 부팅에서 걸린다 — 런타임까지 미뤄지지 않는다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtlDefault,
        Duration refreshTtlExtended
) {
}
