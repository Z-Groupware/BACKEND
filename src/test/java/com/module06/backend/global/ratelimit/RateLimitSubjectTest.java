package com.module06.backend.global.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitSubject")
class RateLimitSubjectTest {

    @Test
    @DisplayName("계정 키에 이메일이 그대로 들어가지 않는다 — Redis 키 목록이 계정 목록이 되면 안 된다")
    void accountKeyIsHashed() {
        String key = RateLimitSubject.ofAccount("8AS2-G8T1", "hayun@zgroup.co.kr");

        assertThat(key)
                .doesNotContain("hayun")
                .doesNotContain("zgroup")
                .doesNotContain("8AS2")
                .matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("기업코드·이메일의 대소문자와 공백을 흡수한다 — 로그인 정규화와 같은 규칙이어야 카운터가 갈리지 않는다")
    void accountKeyIsNormalized() {
        String canonical = RateLimitSubject.ofAccount("8AS2-G8T1", "hayun@zgroup.co.kr");

        assertThat(RateLimitSubject.ofAccount("  8as2-g8t1 ", " HAYUN@zgroup.co.kr ")).isEqualTo(canonical);
    }

    @Test
    @DisplayName("다른 계정은 다른 키다")
    void differentAccountsDiffer() {
        assertThat(RateLimitSubject.ofAccount("A", "x@y.z"))
                .isNotEqualTo(RateLimitSubject.ofAccount("B", "x@y.z"));
    }

    @Test
    @DisplayName("같은 이메일이라도 회사가 다르면 다른 키다 — 멀티테넌시라 이메일만으로는 계정이 아니다")
    void sameEmailInDifferentCompaniesDiffer() {
        assertThat(RateLimitSubject.ofAccount("ACME", "admin@z.co"))
                .isNotEqualTo(RateLimitSubject.ofAccount("OTHER", "admin@z.co"));
    }

    @Test
    @DisplayName("IP 를 못 읽으면 'unknown' 으로 묶는다 — null 키로 카운터가 깨지지 않게")
    void unknownIpFallsBack() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(RateLimitSubject.ofClientIp(request)).isEqualTo("unknown");
    }
}
