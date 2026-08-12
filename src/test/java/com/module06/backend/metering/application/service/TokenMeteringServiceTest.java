package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.RecordTokenUsageCommand;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.TokenUsageOutbox;
import com.module06.backend.metering.domain.model.TokenUsageRecord;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import com.module06.backend.metering.domain.repository.TokenUsageOutboxRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenMeteringServiceTest {

    @Mock
    private TokenUsageRecordRepository tokenUsageRecordRepository;

    @Mock
    private TokenUsageOutboxRepository tokenUsageOutboxRepository;

    @Mock
    private CompanyTokenPlanRepository companyTokenPlanRepository;

    private TokenMeteringService service;

    // 고정 Clock — record 의 recorded_at 과 getStatus 의 월 판정이 실행 시각에 흔들리지 않게 한다.
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        // 원장 반영 규칙(멱등)은 실제 TokenUsageLedgerAppender 를 통과시킨다 — record 경로 전체를 검증하기 위함.
        TokenUsageLedgerAppender appender = new TokenUsageLedgerAppender(tokenUsageRecordRepository);
        service = new TokenMeteringService(
                tokenUsageRecordRepository, tokenUsageOutboxRepository, appender, companyTokenPlanRepository, FIXED_CLOCK);
    }

    @Test
    void recordSavesUsageWithCalculatedTotalTokens() {
        RecordTokenUsageCommand command = command("job-1", 1200, 800);
        when(tokenUsageRecordRepository.existsByJobId("job-1")).thenReturn(false);
        when(tokenUsageRecordRepository.save(any(TokenUsageRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(command);

        ArgumentCaptor<TokenUsageRecord> captor = ArgumentCaptor.forClass(TokenUsageRecord.class);
        verify(tokenUsageRecordRepository).save(captor.capture());
        TokenUsageRecord saved = captor.getValue();
        assertThat(saved.getCompanyId()).isEqualTo(1L);
        assertThat(saved.getTeamId()).isEqualTo(10L);
        assertThat(saved.getMeetingId()).isEqualTo(100L);
        assertThat(saved.getJobId()).isEqualTo("job-1");
        assertThat(saved.getInputTokens()).isEqualTo(1200);
        assertThat(saved.getOutputTokens()).isEqualTo(800);
        assertThat(saved.getTotalTokens()).isEqualTo(2000);
        assertThat(saved.getModel()).isEqualTo("gpt-test");
        assertThat(saved.getRecordedAt()).isNotNull();
        // 정상 경로는 outbox 를 거치지 않는다.
        verify(tokenUsageOutboxRepository, never()).save(any());
    }

    @Test
    void recordIsNoOpWhenJobIdAlreadyExists() {
        RecordTokenUsageCommand command = command("job-1", 1200, 800);
        when(tokenUsageRecordRepository.existsByJobId("job-1")).thenReturn(false, true);
        when(tokenUsageRecordRepository.save(any(TokenUsageRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(command);
        service.record(command);

        verify(tokenUsageRecordRepository, times(1)).save(any(TokenUsageRecord.class));
    }

    @Test
    void recordEnqueuesOutboxWhenLedgerWriteFailsInsteadOfLosingIt() {
        RecordTokenUsageCommand command = command("job-1", 1200, 800);
        when(tokenUsageRecordRepository.existsByJobId("job-1")).thenReturn(false);
        // 원장 반영이 일시적으로 실패(DB 순단 등).
        when(tokenUsageRecordRepository.save(any(TokenUsageRecord.class)))
                .thenThrow(new RuntimeException("db unavailable"));
        when(tokenUsageOutboxRepository.existsByJobId("job-1")).thenReturn(false);
        when(tokenUsageOutboxRepository.save(any(TokenUsageOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        // 예외를 밖으로 던지지 않는다(분석을 실패시키지 않음) — 대신 durable 하게 적재한다.
        service.record(command);

        ArgumentCaptor<TokenUsageOutbox> captor = ArgumentCaptor.forClass(TokenUsageOutbox.class);
        verify(tokenUsageOutboxRepository).save(captor.capture());
        TokenUsageOutbox queued = captor.getValue();
        assertThat(queued.getJobId()).isEqualTo("job-1");
        assertThat(queued.getInputTokens()).isEqualTo(1200);
        assertThat(queued.getOutputTokens()).isEqualTo(800);
        assertThat(queued.getStatus().name()).isEqualTo("PENDING");
        assertThat(queued.getAttemptCount()).isZero();
    }

    @Test
    void commandValidationFailureThrowsBusinessException() {
        assertThatThrownBy(() -> command("job-1", -1, 800))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MeteringErrorCode.MT_RECORD_COMMAND_INVALID));
    }

    private static RecordTokenUsageCommand command(String jobId, int inputTokens, int outputTokens) {
        return new RecordTokenUsageCommand(1L, 10L, 100L, jobId, inputTokens, outputTokens, "gpt-test");
    }
}
