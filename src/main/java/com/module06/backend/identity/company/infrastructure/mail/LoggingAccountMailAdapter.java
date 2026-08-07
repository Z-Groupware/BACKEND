package com.module06.backend.identity.company.infrastructure.mail;

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
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingAccountMailAdapter implements AccountMailPort {

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
