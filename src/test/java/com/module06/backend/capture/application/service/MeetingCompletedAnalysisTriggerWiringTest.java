package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * MEET-08 이벤트 → 자동 분석 <b>배선</b>.
 *
 * <p>이 작업의 실체는 "이벤트에 받는 쪽이 생겼다"이다. 리스너 로직이 맞아도 애너테이션이
 * 안 먹으면 회의를 끝내도 여전히 아무 일이 일어나지 않고, 그건 단위 테스트로는 절대 보이지
 * 않는다 — 그래서 컨텍스트를 띄워 확인한다.
 *
 * <p>두 가지를 함께 고정한다.
 * <ul>
 *   <li><b>커밋된 뒤에만</b> 분석한다 — 롤백된 트랜잭션의 이벤트는 아무것도 시키지 않는다.
 *       (일어나지 않은 회의 종료에 토큰을 태우는 경로다)</li>
 *   <li>분석이 <b>발행 스레드를 붙잡지 않는다</b> — MEET-08 응답이 분석을 기다리면 안 된다</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("MEET-08 이벤트 배선 — 커밋 뒤 · 다른 스레드")
class MeetingCompletedAnalysisTriggerWiringTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 4_242L;
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 6, 15, 2, 40);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /*
     * 계층을 실제로 돌리지 않는다 — 여기서 보는 것은 호출이 **도달하는가**다.
     *
     * 유스케이스(RunAnalysisUseCase)가 아니라 그 아래를 목으로 둔다. AnalysisService 하나가
     * 유스케이스 셋을 함께 구현하고 있어서, 인터페이스 하나만 목으로 갈아끼우면 같은 빈을
     * 다른 타입으로 받는 컨트롤러가 부팅에서 죽는다. 아래를 막으면 유스케이스의 실제 판정
     * (중복 실행 · 완료 여부)까지 함께 지나므로 검증 범위도 넓어진다.
     */
    @MockitoBean
    private AnalysisOrchestrator analysisOrchestrator;

    /* 회의 행이 없는 테스트다. 회사 스코프 관문은 여기서 볼 대상이 아니다(MeetingAccessGuardTest 가 본다). */
    @MockitoBean
    private MeetingAccessGuard meetingAccessGuard;

    /* 회의 행이 없는 테스트라 길이를 읽을 수 없다. 하한 검사가 아니라 배선이 검증 대상이다. */
    @MockitoBean
    private MeetingLengthProvider meetingLengthProvider;

    @Test
    @DisplayName("커밋되면 분석이 시작되고, 발행 스레드에서 돌지 않는다")
    void 커밋_뒤에_다른_스레드에서_분석이_시작된다() throws Exception {
        CountDownLatch called = new CountDownLatch(1);
        AtomicBoolean sameThread = new AtomicBoolean(true);
        Thread publisherThread = Thread.currentThread();

        when(meetingLengthProvider.actualLengthOf(anyLong()))
                .thenReturn(Optional.of(Duration.ofMinutes(30)));
        when(analysisOrchestrator.run(anyLong(), anyLong(), anyLong(), anyList(), anyBoolean()))
                .thenAnswer(invocation -> {
                    sameThread.set(Thread.currentThread().equals(publisherThread));
                    called.countDown();
                    return AnalysisOutcome.done(2);
                });

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(event(MEETING)));

        assertThat(called.await(10, TimeUnit.SECONDS))
                .as("커밋 뒤 분석이 시작돼야 한다 — 리스너가 등록되지 않으면 여기서 멈춘다")
                .isTrue();
        assertThat(sameThread)
                .as("발행 스레드에서 돌면 MEET-08 응답이 분석을 기다리게 된다")
                .isFalse();
    }

    @Test
    @DisplayName("롤백되면 분석하지 않는다 — 일어나지 않은 회의 종료에 토큰을 쓰지 않는다")
    void 롤백된_트랜잭션은_분석을_시작하지_않는다() throws Exception {
        CountDownLatch called = new CountDownLatch(1);

        when(meetingLengthProvider.actualLengthOf(anyLong()))
                .thenReturn(Optional.of(Duration.ofMinutes(30)));
        when(analysisOrchestrator.run(anyLong(), anyLong(), anyLong(), anyList(), anyBoolean()))
                .thenAnswer(invocation -> {
                    called.countDown();
                    return AnalysisOutcome.done(2);
                });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event(MEETING + 1));
            status.setRollbackOnly();
        });

        assertThat(called.await(2, TimeUnit.SECONDS))
                .as("롤백된 트랜잭션의 이벤트로 분석이 돌면 안 된다")
                .isFalse();
    }

    /* 종료 시각은 고정값이다 — 배선 검증에 흐르는 시간이 필요 없다. */
    private MeetingCompletionRequestedEvent event(long meetingId) {
        return new MeetingCompletionRequestedEvent(COMPANY, meetingId, 900L, COMPLETED_AT);
    }
}
