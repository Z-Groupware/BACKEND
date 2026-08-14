package com.module06.backend.identity.company.infrastructure.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.identity.company.application.port.out.AccountMailPort;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬/테스트용. SMTP 를 실제로 태우지 않고 발송 대신 로그로 남긴다.
 *
 * <p>로컬에서 비밀번호를 눈으로 보고 곧바로 로그인까지 확인할 수 있어, 등록→로그인 흐름 전체를
 * SMTP 계정 없이도 검증할 수 있다. 운영은 {@link GmailAccountMailAdapter} 가 대신한다.
 *
 * <p>비밀번호 평문이 로그에 남으므로 {@code @Profile("!prod")} 로 운영에서 절대 뜨지 않게 막는다.
 * 다만 그 방어는 <b>프로파일이 실제로 설정돼 있을 때만</b> 성립한다 — 설정이 빠지면 프로파일이
 * 비고, 비면 {@code !prod} 가 참이 되어 이 빈이 운영에서 뜬다. 아래 생성자 가드가 그 경우를 잡는다.
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingAccountMailAdapter implements AccountMailPort {

    /**
     * 프로파일 설정이 빠진 채 운영에서 뜬 것을 부팅 시점에 잡는다.
     *
     * <p>판단 근거는 {@code spring.mail.username} 이다. 이 값은 SSM 이 운영에만 주입하므로
     * (로컬·테스트는 {@code ${MAIL_USERNAME:}} 의 기본값으로 비어 있다), <b>값이 채워져 있는데
     * 이 빈이 뜬다</b>는 것은 "운영 설정을 받았는데 프로파일은 운영이 아니다" 라는 뜻이다.
     * 그 상태로 계속 뜨면 발급 비밀번호가 평문으로 로그에 남는다 — 변경 기능이 없어 영구 비밀번호다.
     *
     * <p>여기서 죽이는 편이 낫다. 조용히 뜨면 아무도 모르는 채로 로그가 쌓이고, 그 로그는
     * json-file 로 디스크에 남는다. 부팅 실패는 즉시 눈에 띄고 되돌리기도 쉽다.
     *
     * <p>1차 방어선은 {@code infra/docker-compose.yml} 이 {@code SPRING_PROFILES_ACTIVE: prod} 를
     * 직접 박는 것이다. 이 가드는 그 경로를 벗어난 기동(컨테이너 밖에서 {@code java -jar} 등)까지
     * 덮는 2차선이다.
     */
    public LoggingAccountMailAdapter(@Value("${spring.mail.username:}") String configuredMailUsername) {
        if (!configuredMailUsername.isBlank()) {
            throw new IllegalStateException("""
                    운영 메일 설정(spring.mail.username)이 주입됐는데 프로파일이 prod 가 아니다.
                    이대로 뜨면 발급 비밀번호가 평문으로 로그에 남는다(변경 기능이 없어 영구 비밀번호다).
                    SPRING_PROFILES_ACTIVE=prod 를 설정하라 — 배포라면 infra/docker-compose.yml 의
                    environment 블록이 이미 박아 두므로, 그 경로를 벗어난 기동인지 먼저 확인하라.""");
        }
    }

    @Override
    public void sendAccountIssued(String toEmail, String companyCode, String password) {
        log.warn("""
                [계정 발급 메일 — 미발송, 로그 대체]
                  받는 사람 : {}
                  기업 코드 : {}
                  비밀번호  : {}
                """, toEmail, companyCode, password);
    }
}
