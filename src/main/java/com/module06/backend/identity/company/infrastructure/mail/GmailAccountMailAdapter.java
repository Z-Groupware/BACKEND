package com.module06.backend.identity.company.infrastructure.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.module06.backend.identity.company.application.port.out.AccountMailPort;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 운영용 SMTP 구현 — Gmail 계정(앱 비밀번호)으로 발송한다. {@code application.yaml} 의
 * {@code spring.mail.*} 설정(host/port/username/password)을 {@link JavaMailSender} 가 그대로 쓴다.
 *
 * <p>{@link AccountMailPort} 계약대로 예외를 던지지 않는다 — 메일 서버가 느리거나 죽어도 방금
 * 만든 회사를 롤백할 이유가 없다. 실패는 로그로 남기고, 코드는 이미 DB 에 있으니 재발송으로 푼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("prod")
public class GmailAccountMailAdapter implements AccountMailPort {

    private final JavaMailSender mailSender;

    @Override
    public void sendAccountIssued(String toEmail, String companyCode, String password) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setTo(toEmail);
            helper.setSubject("[Z-Groupware] 계정이 발급됐습니다");
            helper.setText(buildHtml(companyCode, password), true);

            mailSender.send(mimeMessage);
            log.info("계정 발급 메일 발송 성공: to={}", maskEmail(toEmail));
        } catch (Exception exception) {
            /* MailException(발송) 뿐 아니라 MessagingException(메시지 조립)도 여기서 잡는다 —
             * 어느 쪽이든 회사·오너 계정 생성 자체는 이미 끝난 뒤라 던질 이유가 없다. */
            log.error("계정 발급 메일 발송 실패: to={}", maskEmail(toEmail), exception);
        }
    }

    /*
     * 계정 발급과 달리 성공 여부를 돌려준다(포트 javadoc). 여기서 false 를 주면 호출자가
     * 새 비밀번호를 저장하지 않으므로, 사용자의 기존 비밀번호는 그대로 살아 있다.
     */
    @Override
    public boolean sendPasswordReset(String toEmail, String companyCode, String password) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setTo(toEmail);
            helper.setSubject("[Z-Groupware] 새 비밀번호가 발급됐습니다");
            helper.setText(buildResetHtml(companyCode, password), true);

            mailSender.send(mimeMessage);
            log.info("비밀번호 재발급 메일 발송 성공: to={}", maskEmail(toEmail));
            return true;
        } catch (Exception exception) {
            log.error("비밀번호 재발급 메일 발송 실패: to={}", maskEmail(toEmail), exception);
            return false;
        }
    }

    private String buildHtml(String companyCode, String password) {
        return """
                <div style="font-family: sans-serif; line-height: 1.6;">
                  <h2>Z-Groupware 계정이 발급됐습니다</h2>
                  <p>아래 정보로 로그인해 주세요.</p>
                  <table style="border-collapse: collapse;">
                    <tr><td style="padding: 4px 12px 4px 0;">기업 코드</td><td><b>%s</b></td></tr>
                    <tr><td style="padding: 4px 12px 4px 0;">비밀번호</td><td><b>%s</b></td></tr>
                  </table>
                  <p>비밀번호는 안전한 곳에 보관해 주세요.</p>
                  <p>로그인 후 마이페이지에서 직접 정한 비밀번호로 바꿀 수 있어요.</p>
                </div>
                """.formatted(companyCode, password);
    }

    private String buildResetHtml(String companyCode, String password) {
        return """
                <div style="font-family: sans-serif; line-height: 1.6;">
                  <h2>새 비밀번호가 발급됐습니다</h2>
                  <p>요청하신 계정의 비밀번호를 아래 값으로 바꿨습니다. 이전 비밀번호는 더 이상 쓸 수 없어요.</p>
                  <table style="border-collapse: collapse;">
                    <tr><td style="padding: 4px 12px 4px 0;">기업 코드</td><td><b>%s</b></td></tr>
                    <tr><td style="padding: 4px 12px 4px 0;">새 비밀번호</td><td><b>%s</b></td></tr>
                  </table>
                  <p>로그인 후 마이페이지에서 직접 정한 비밀번호로 바꿀 수 있어요.</p>
                  <p>본인이 요청하지 않았다면 관리자에게 알려 주세요.</p>
                </div>
                """.formatted(companyCode, password);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "*".repeat(email.length());
        }
        return email.charAt(0) + "*".repeat(atIndex - 1) + email.substring(atIndex);
    }
}
