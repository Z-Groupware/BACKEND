package com.module06.backend.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtlDefault,
        Duration refreshTtlExtended
) {
}
