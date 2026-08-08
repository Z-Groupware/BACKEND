package com.module06.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * CORS 설정이 틀리면 서버는 조용하고 브라우저만 막는다 — 서버 로그에 아무것도 안 남아서
 * "프론트에서만 안 된다"로 하루를 쓴다. 그래서 값 검증을 부팅으로 당겨두고, 그 관문을 여기서 지킨다.
 */
@DisplayName("CorsProperties")
class CorsPropertiesTest {

    @Test
    @DisplayName("와일드카드는 거부한다 — \"*\" 는 모든 사이트에 API 를 여는 것이다")
    void rejectsWildcard() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("와일드카드");
    }

    @Test
    @DisplayName("서브도메인 패턴도 거부한다 — setAllowedOrigins 는 패턴을 매칭하지 않아 조용히 아무도 못 지나간다")
    void rejectsSubdomainPattern() {
        assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("와일드카드");
    }

    @Test
    @DisplayName("빈 목록은 거부한다 — 아무도 허용 안 하는 상태로 조용히 뜨면 안 된다")
    void rejectsEmptyList() {
        assertThatThrownBy(() -> new CorsProperties(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비어 있다");
    }

    @Test
    @DisplayName("설정 자체가 없으면(null) 거부한다")
    void rejectsNull() {
        assertThatThrownBy(() -> new CorsProperties(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비어 있다");
    }

    @Test
    @DisplayName("목록 안의 빈 값·공백만 있는 값도 거부한다")
    void rejectsBlankEntry() {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:3000", "   ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("빈 값");

        assertThatThrownBy(() -> new CorsProperties(Arrays.asList("http://localhost:3000", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("빈 값");
    }

    @Test
    @DisplayName("앞뒤 공백은 잘라낸다 — YAML 목록으로 직접 쓴 값은 스프링이 안 잘라준다")
    void trimsSurroundingWhitespace() {
        CorsProperties properties = new CorsProperties(List.of(" http://localhost:3000 ", "https://z.example.com"));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:3000", "https://z.example.com");
    }

    @Test
    @DisplayName("정상 오리진은 그대로 통과한다")
    void acceptsValidOrigins() {
        CorsProperties properties = new CorsProperties(List.of("http://localhost:3000"));

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:3000");
    }
}
