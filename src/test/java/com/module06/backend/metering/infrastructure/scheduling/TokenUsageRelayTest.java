package com.module06.backend.metering.infrastructure.scheduling;

import com.module06.backend.metering.application.service.TokenUsageLedgerAppender;
import com.module06.backend.metering.domain.model.OutboxStatus;
import com.module06.backend.metering.domain.model.TokenUsageOutbox;
import com.module06.backend.metering.domain.repository.TokenUsageOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageRelayTest {

    @Mock
    private TokenUsageOutboxRepository tokenUsageOutboxRepository;

    @Mock
    private TokenUsageLedgerAppender ledgerAppender;

    private TokenUsageRelay relay;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        relay = new TokenUsageRelay(tokenUsageOutboxRepository, ledgerAppender, FIXED_CLOCK);
    }

    @Test
    void recoversPendingItemIntoLedgerAndMarksDone() {
        TokenUsageOutbox item = pending(0);
        when(tokenUsageOutboxRepository.findDuePending(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(item));
        when(ledgerAppender.append(any())).thenReturn(true);
        when(tokenUsageOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int delivered = relay.drainOnce();

        assertThat(delivered).isEqualTo(1);
        TokenUsageOutbox saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void reschedulesWithBackoffOnTransientFailure() {
        TokenUsageOutbox item = pending(0);
        when(tokenUsageOutboxRepository.findDuePending(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(item));
        when(ledgerAppender.append(any())).thenThrow(new RuntimeException("db down"));
        when(tokenUsageOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int delivered = relay.drainOnce();

        assertThat(delivered).isZero();
        TokenUsageOutbox saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);   // 여전히 재시도 대상
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getNextAttemptAt()).isAfter(NOW);               // 뒤로 밀렸다(백오프)
        assertThat(saved.getLastError()).contains("db down");
    }

    @Test
    void deadLettersAfterMaxAttempts() {
        // 이미 MAX_ATTEMPTS-1 회 실패한 항목 → 이번 실패로 한계 도달 → DEAD.
        TokenUsageOutbox item = pending(TokenUsageRelay.MAX_ATTEMPTS - 1);
        when(tokenUsageOutboxRepository.findDuePending(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(item));
        when(ledgerAppender.append(any())).thenThrow(new RuntimeException("still down"));
        when(tokenUsageOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int delivered = relay.drainOnce();

        assertThat(delivered).isZero();
        TokenUsageOutbox saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.DEAD);      // 자동 복구 포기(관측 가능)
        assertThat(saved.getAttemptCount()).isEqualTo(TokenUsageRelay.MAX_ATTEMPTS);
    }

    private static TokenUsageOutbox pending(int attemptCount) {
        return TokenUsageOutbox.restore(1L, 1L, 10L, 100L, "job-1", 1200, 800, "gpt-test",
                OutboxStatus.PENDING, attemptCount, null, NOW.minusMinutes(5), NOW.minusMinutes(5), NOW.minusMinutes(5));
    }

    private TokenUsageOutbox captureSaved() {
        ArgumentCaptor<TokenUsageOutbox> captor = ArgumentCaptor.forClass(TokenUsageOutbox.class);
        org.mockito.Mockito.verify(tokenUsageOutboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
