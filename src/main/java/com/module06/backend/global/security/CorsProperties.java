package com.module06.backend.global.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 프론트가 API 를 부르는 오리진 화이트리스트다 — 배포 도메인이 정해지면 여기(설정)만 늘리고 코드는 안 건드린다. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
