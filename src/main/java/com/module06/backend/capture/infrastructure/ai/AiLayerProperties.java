package com.module06.backend.capture.infrastructure.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Set;

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
 *
 * <h2>설정이 비면 부팅에서 막는다</h2>
 * 비운 채로 뜨면 매 호출이 401 로 실패하는데, 그건 런타임에 가서야 드러나고 그때는 이미
 * 잡이 돌고 있다. 그래서 생성 시점에 던진다 — 스프링이 컨텍스트 기동을 실패시킨다.
 * (Python 쪽도 대칭이다: INTERNAL_TOKEN 이 없으면 인증을 통과시키지 않고 거절한다.)
 */
@ConfigurationProperties(prefix = "ai")
public record AiLayerProperties(
        String baseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout
) {

    /*
     * 평문 HTTP 를 허용하는 호스트. 두 EC2 는 같은 VPC 사설 서브넷이고 그 구간에 TLS 를
     * 두지 않는 것이 인프라 결정이므로, 사설·루프백 주소는 http 를 그대로 허용한다.
     *
     * 다만 **공개 호스트로 향하는 http 는 막는다.** X-Internal-Token 이 모든 요청에 붙기
     * 때문에, base-url 을 공개 주소로 잘못 넣으면 공유 시크릿이 평문으로 인터넷을 지난다
     * (CWE-319). 오타 하나가 토큰 유출이 되는 경로라 설정 단계에서 끊는다.
     */
    private static final Set<String> PLAINTEXT_ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "qdrant");

    public AiLayerProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofMinutes(3);

        requireText(baseUrl, "ai.base-url");
        requireText(internalToken, "ai.internal-token");
        requireNoPlaintextTokenLeak(baseUrl);
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 가 비어 있습니다. "
                    + "AI 계층 호출이 전부 실패하므로 부팅을 중단합니다 "
                    + "(운영은 SSM /z/prod/, 로컬은 application-secret.yml).");
        }
    }

    private static void requireNoPlaintextTokenLeak(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("ai.base-url 이 URL 형식이 아닙니다: " + baseUrl, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("http")) {
            return;   // https 이거나 스킴이 없다(상대 경로는 RestClient 가 거른다)
        }

        String host = uri.getHost();
        if (host == null || isPrivate(host)) {
            return;
        }
        throw new IllegalStateException("ai.base-url 이 공개 호스트로 향하는 http 입니다: " + host
                + ". X-Internal-Token 이 평문으로 나갑니다(CWE-319) — https 를 쓰거나 "
                + "VPC 사설 주소를 쓸 것.");
    }

    /* 사설·루프백만 평문 허용. RFC1918 + 100.64/10(캐리어 그레이드 NAT, AWS 가 쓴다). */
    private static boolean isPrivate(String host) {
        String lower = host.toLowerCase();
        if (PLAINTEXT_ALLOWED_HOSTS.contains(lower)) {
            return true;
        }
        return lower.startsWith("10.")
                || lower.startsWith("192.168.")
                || lower.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*")
                || lower.matches("^100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\..*");
    }
}
