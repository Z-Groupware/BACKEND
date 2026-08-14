package com.module06.backend.identity.company.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 이 어댑터는 발급 비밀번호를 평문으로 로그에 찍는다. @Profile("!prod") 로 운영에서 막지만,
 * 그 방어는 프로파일이 실제로 설정돼 있을 때만 성립한다 — 설정이 빠지면 프로파일이 비고,
 * 비면 !prod 가 참이 되어 운영에서 뜬다. 그 경우를 생성자 가드가 잡는지 검증한다.
 *
 * 판단 근거인 spring.mail.username 은 SSM 이 운영에만 주입한다(로컬·테스트는 ${MAIL_USERNAME:}
 * 기본값으로 빈 문자열). 채워져 있는데 이 빈이 뜬다 = 운영 설정인데 프로파일이 운영이 아니다.
 */
@DisplayName("LoggingAccountMailAdapter 부팅 가드")
class LoggingAccountMailAdapterTest {

    @Test
    @DisplayName("운영 메일 설정이 주입된 채 뜨면 부팅에 실패한다 — 설정 누락이 유출이 되지 않게")
    void failsFastWhenProductionMailIsConfigured() {
        assertThatThrownBy(() -> new LoggingAccountMailAdapter("z-noreply@company.co.kr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    @DisplayName("로컬·테스트는 메일 설정이 비어 있으므로 그대로 뜬다")
    void bootsWhenMailIsNotConfigured() {
        assertThatCode(() -> new LoggingAccountMailAdapter("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("공백만 든 값도 미설정으로 본다 — SSM 파라미터가 빈 문자열로 들어오는 경우")
    void treatsBlankAsNotConfigured() {
        assertThatCode(() -> new LoggingAccountMailAdapter("   ")).doesNotThrowAnyException();
    }
}
