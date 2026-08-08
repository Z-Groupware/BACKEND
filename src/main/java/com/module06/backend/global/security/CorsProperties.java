package com.module06.backend.global.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프론트가 API 를 부르는 오리진 화이트리스트다 — 배포 도메인이 정해지면 여기(설정)만 늘리고 코드는 안 건드린다.
 *
 * <p>값이 틀리면 부팅에서 끊는다. CORS 는 틀려도 서버가 조용하고 브라우저만 막아서, 런타임까지
 * 미루면 "서버 로그는 멀쩡한데 프론트에서만 안 되는" 증상으로 나타난다 — 원인을 찾는 데 오래 걸린다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            // 빈 목록은 "아무도 허용 안 함"이라 CORS 가 조용히 전부 막힌다 — 껐다는 신호가 어디에도 없다.
            throw new IllegalStateException(
                    "app.cors.allowed-origins 가 비어 있다. 프론트 오리진을 최소 하나 지정하라"
                            + " (예: CORS_ALLOWED_ORIGINS=http://localhost:3000).");
        }

        // 콤마 구분 환경변수는 스프링이 이미 잘라주지만, YAML 목록으로 직접 쓴 값은 공백이 남는다.
        allowedOrigins = allowedOrigins.stream()
                .map(origin -> origin == null ? "" : origin.trim())
                .toList();

        for (String origin : allowedOrigins) {
            if (origin.isBlank()) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins 에 빈 값이 있다: " + allowedOrigins);
            }
            if ("null".equalsIgnoreCase(origin)) {
                /*
                 * 문자열 "null" 은 자바 null 이 아니라 실제 오리진 값으로 매칭된다 — 허용하면
                 * Origin: null 요청에 Access-Control-Allow-Origin: null 을 돌려준다.
                 * 브라우저가 그 헤더를 보내는 맥락(샌드박스 iframe · file:// · data: URL)은
                 * 공격자가 직접 만들 수 있으므로, 아무 사이트에나 여는 것과 같아진다.
                 * 설정 템플릿이 빈 값을 "null" 로 렌더링하면 조용히 이 상태가 된다.
                 */
                throw new IllegalStateException(
                        "app.cors.allowed-origins 에 \"null\" 을 쓸 수 없다."
                                + " Origin: null 은 샌드박스 iframe·file:// 처럼 공격자가 만들 수 있는 맥락이 보낸다.");
            }
            if (origin.contains("*")) {
                /*
                 * "*" 는 모든 사이트에서 이 API 를 부를 수 있게 연다. 토큰이 쿠키가 아니라 헤더로
                 * 오가서 남의 세션을 타지는 못하지만, 공개 엔드포인트(로그인·기업 조회)가 아무
                 * 사이트에나 열린다. "https://*.example.com" 같은 패턴도 막는다 —
                 * setAllowedOrigins 는 패턴을 매칭하지 않아 아무도 통과하지 못하는데 조용하다.
                 */
                throw new IllegalStateException(
                        "app.cors.allowed-origins 에 와일드카드를 쓸 수 없다: \"" + origin + "\"."
                                + " 허용할 오리진을 그대로 나열하라.");
            }
        }
    }
}
