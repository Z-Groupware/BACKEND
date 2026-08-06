package com.module06.backend.identity.company.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * AccountMailPort 계약대로 예외를 던지지 않는지 검증한다 — 회사·오너 계정 생성은 이미 커밋된
 * 뒤라, 메일 발송이 실패해도 호출자(CompanyRegistrationService)가 롤백할 이유가 없다.
 */
@DisplayName("GmailAccountMailAdapter")
class GmailAccountMailAdapterTest {

    @Test
    @DisplayName("정상 발송하면 send를 호출한다")
    void sendsMail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        GmailAccountMailAdapter adapter = new GmailAccountMailAdapter(mailSender);

        adapter.sendAccountIssued("owner@new.co.kr", "8AS2-G8T1", "tempPw123!");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("발송이 실패해도 예외를 던지지 않는다 — 계정 생성을 롤백할 이유가 없다")
    void doesNotPropagateOnSendFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        doThrow(new MailSendException("SMTP 다운")).when(mailSender).send(any(MimeMessage.class));
        GmailAccountMailAdapter adapter = new GmailAccountMailAdapter(mailSender);

        assertThatCode(() -> adapter.sendAccountIssued("owner@new.co.kr", "8AS2-G8T1", "tempPw123!"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("메시지 조립이 실패해도 예외를 던지지 않는다")
    void doesNotPropagateOnAssemblyFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("메시지 생성 실패"));
        GmailAccountMailAdapter adapter = new GmailAccountMailAdapter(mailSender);

        assertThatCode(() -> adapter.sendAccountIssued("owner@new.co.kr", "8AS2-G8T1", "tempPw123!"))
                .doesNotThrowAnyException();
    }

    /** MimeMessageHelper 가 헤더·본문을 실제로 세팅해야 해서 순수 mock이 아니라 진짜 인스턴스가 필요하다. */
    private MimeMessage realMimeMessage() {
        return new JavaMailSenderImpl().createMimeMessage();
    }
}
