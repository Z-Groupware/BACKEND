package com.module06.backend.identity.company.infrastructure.mail;

import org.springframework.stereotype.Component;

import com.module06.backend.identity.company.application.port.out.AccountMailPort;

import lombok.extern.slf4j.Slf4j;

/**
 * 임시 구현. SMTP 가 아직 없어 발송 대신 로그로 남긴다.
 *
 * <p>포트를 먼저 뚫고 구현을 뒤로 미루는 이유는, 여기서 메일 인프라를 붙이면 기업 등록 작업이
 * 메일 설정 작업으로 번지기 때문이다. 로그로 남기면 로컬에서 비밀번호를 눈으로 보고 곧바로
 * 로그인까지 확인할 수 있어, 등록→로그인 흐름 전체를 지금 검증할 수 있다.
 *
 * <p>⚠️ 운영에 이대로 나가면 안 된다. 비밀번호 평문이 애플리케이션 로그에 남는다.
 * 실제 SMTP 구현으로 교체할 때 이 클래스는 지운다.
 */
@Slf4j
@Component
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
