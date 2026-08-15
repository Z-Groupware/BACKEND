package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.event.AnalysisCompletedEvent;
import com.module06.backend.capture.application.event.AnalysisFailedEvent;
import com.module06.backend.capture.application.event.SttTranscriptCompletedEvent;
import com.module06.backend.capture.application.port.out.AnalysisEventPublisher;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * MEET-08 회의 종료 → 자동 분석.
 *
 * <p>검증의 축은 <b>부르는가 · 안 부르는가</b> 둘이다. 잘못 부르면 토큰이 나가고, 잘못 안 부르면
 * 사용자는 분석이 안 됐다는 사실조차 모른 채 빈 요약 화면을 본다.
 *
 * <p>실행 흐름(AFTER_COMMIT · 비동기)은 여기서 검증하지 않는다 — 그건 스프링이 하는 일이고,
 * 애너테이션이 붙었는지는 컨텍스트를 띄우는 테스트가 본다.
 */
class MeetingCompletedAnalysisTriggerTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long HOST = 3L;
    private static final String TITLE = "스프린트 회고";
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 6, 15, 2, 40);

    private static final MeetingTitleProvider DEFAULT_TITLE_PROVIDER = meetingId -> Optional.of(TITLE);
    private static final MeetingHostProvider DEFAULT_HOST_PROVIDER = meetingId -> Optional.of(HOST);
    private static final MeetingCompanyProvider DEFAULT_COMPANY_PROVIDER = meetingId ->
            Optional.of(new MeetingCompanyProvider.AutomaticAnalysisTarget(COMPANY, true));

    @Test
    @DisplayName("회의가 끝나면 분석을 부른다 — force 없이 부른다")
    void 회의_종료가_분석을_시작한다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        trigger(analysis, Duration.ofMinutes(42)).onMeetingCompleted(event());

        assertThat(analysis.calls).hasSize(1);
        assertThat(analysis.calls.get(0).companyId()).isEqualTo(COMPANY);
        assertThat(analysis.calls.get(0).meetingId()).isEqualTo(MEETING);
        /*
         * 자동 경로는 force 를 쓰지 않는다. 이미 완료된 회의를 다시 태우면 재과금이고,
         * 강제 재실행은 별도 권한과 확인 모달을 요구하는 사람의 판단이다(명세 ANLZ-01).
         */
        assertThat(analysis.calls.get(0).force()).isFalse();
    }

    @Test
    @DisplayName("STT 전체 완료도 기존 자동 분석 경로를 force 없이 호출한다")
    void STT_전체_완료가_분석을_시작한다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        trigger(analysis, Duration.ofMinutes(42))
                .onSttTranscriptCompleted(new SttTranscriptCompletedEvent(MEETING));

        assertThat(analysis.calls).singleElement().satisfies(call -> {
            assertThat(call.companyId()).isEqualTo(COMPANY);
            assertThat(call.meetingId()).isEqualTo(MEETING);
            assertThat(call.force()).isFalse();
        });
    }

    @Test
    @DisplayName("STT 완료 회의의 회사를 읽지 못하면 분석하지 않는다")
    void STT_완료_회의가_없으면_분석하지_않는다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();
        MeetingCompanyProvider missing = meetingId -> Optional.empty();
        MeetingCompletedAnalysisTrigger trigger = new MeetingCompletedAnalysisTrigger(
                analysis, meetingId -> Optional.of(Duration.ofMinutes(42)), missing,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher());

        trigger.onSttTranscriptCompleted(new SttTranscriptCompletedEvent(MEETING));

        assertThat(analysis.calls).isEmpty();
    }

    @Test
    @DisplayName("진행 중 회의의 현재 블록이 전부 끝나도 분석을 시작하지 않는다")
    void 진행_중_회의는_STT_완료만으로_분석하지_않는다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();
        MeetingCompanyProvider inProgress = meetingId ->
                Optional.of(new MeetingCompanyProvider.AutomaticAnalysisTarget(COMPANY, false));
        MeetingCompletedAnalysisTrigger trigger = new MeetingCompletedAnalysisTrigger(
                analysis, meetingId -> Optional.of(Duration.ofMinutes(42)), inProgress,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher());

        trigger.onSttTranscriptCompleted(new SttTranscriptCompletedEvent(MEETING));

        assertThat(analysis.calls).isEmpty();
    }

    @Test
    @DisplayName("3분 미만 회의는 부르지 않는다 — 자동 트리거의 비용 관문이다")
    void 너무_짧은_회의는_분석하지_않는다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        trigger(analysis, Duration.ofSeconds(150)).onMeetingCompleted(event());

        assertThat(analysis.calls).isEmpty();
    }

    @Test
    @DisplayName("Online meetings bypass the 3 minute auto-analysis lower bound")
    void onlineMeetingBypassesLengthLowerBound() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();
        MeetingLengthProvider onlineZeroLength = new MeetingLengthProvider() {
            @Override
            public Optional<Duration> actualLengthOf(long meetingId) {
                return Optional.of(Duration.ZERO);
            }

            @Override
            public Optional<Boolean> isOnline(long meetingId) {
                return Optional.of(true);
            }
        };

        new MeetingCompletedAnalysisTrigger(analysis, onlineZeroLength, DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher())
                .onMeetingCompleted(event());

        assertThat(analysis.calls).hasSize(1);
    }

    @Test
    @DisplayName("Offline meetings keep the 3 minute auto-analysis lower bound")
    void offlineMeetingKeepsLengthLowerBound() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();
        MeetingLengthProvider offlineShort = new MeetingLengthProvider() {
            @Override
            public Optional<Duration> actualLengthOf(long meetingId) {
                return Optional.of(Duration.ofSeconds(150));
            }

            @Override
            public Optional<Boolean> isOnline(long meetingId) {
                return Optional.of(false);
            }
        };

        new MeetingCompletedAnalysisTrigger(analysis, offlineShort, DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher())
                .onMeetingCompleted(event());

        assertThat(analysis.calls).isEmpty();
    }

    @Test
    @DisplayName("하한을 넘으면 부른다 — 경계에서 3분은 통과다")
    void 하한과_같은_길이는_분석한다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        trigger(analysis, Duration.ofMinutes(3)).onMeetingCompleted(event());

        assertThat(analysis.calls).hasSize(1);
    }

    @Test
    @DisplayName("길이를 못 읽으면 부른다 — 모르는 것과 짧은 것은 다르다")
    void 길이를_모르면_분석한다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        /*
         * 건너뛰는 쪽으로 기울면 멀쩡한 회의의 분석이 조용히 사라지고, 사용자는 분석이 안 됐다는
         * 사실조차 모른다. 반대 방향의 손해는 토큰이고 그건 로그에 남는다.
         */
        new MeetingCompletedAnalysisTrigger(analysis, meetingId -> Optional.empty(), DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher())
                .onMeetingCompleted(event());

        assertThat(analysis.calls).hasSize(1);
    }

    @Test
    @DisplayName("길이 조회가 터져도 부른다 — DB 가 흔들렸다고 회의 분석이 사라지면 안 된다")
    void 길이_조회가_실패해도_분석한다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        /*
         * 조회 실패도 "길이를 모르는 것"이다. 여기서 예외가 밖으로 나가면 트리거가 그것을
         * 「분석 실패」로 기록하고 분석은 시작조차 되지 않는다 — 하한 검사가 하려던 일이 아니다.
         */
        MeetingLengthProvider exploding = meetingId -> {
            throw new IllegalStateException("커넥션 풀이 말랐다");
        };

        new MeetingCompletedAnalysisTrigger(analysis, exploding, DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher())
                .onMeetingCompleted(event());

        assertThat(analysis.calls).hasSize(1);
    }

    @Test
    @DisplayName("이미 진행 중이면 조용히 넘어간다 — 중복이 걸러진 정상 동작이다")
    void 이미_진행_중이면_예외를_밖으로_내지_않는다() {
        RunAnalysisUseCase failing = (companyId, meetingId, force) -> {
            throw new BusinessException(CaptureErrorCode.ANALYSIS_ALREADY_RUNNING);
        };

        /*
         * 여기서 던지면 갈 곳이 없다 — AFTER_COMMIT 리스너의 예외는 이미 커밋된 트랜잭션을
         * 되돌리지 못하고, 비동기 스레드라 요청자에게 닿지도 않는다.
         */
        assertThatCode(() -> trigger(failing, Duration.ofMinutes(42))
                .onMeetingCompleted(event()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("분석이 터져도 회의 종료를 되돌리지 않는다 — 예외를 밖으로 내지 않는다")
    void 분석_실패가_밖으로_나가지_않는다() {
        RunAnalysisUseCase exploding = (companyId, meetingId, force) -> {
            throw new IllegalStateException("계층 호출이 터졌다");
        };

        assertThatCode(() -> trigger(exploding, Duration.ofMinutes(42))
                .onMeetingCompleted(event()))
                .doesNotThrowAnyException();
    }

    /* DONE이면 개설자에게 완료 이벤트가 발행되는지 검증한다. */
    @Test
    @DisplayName("분석이 DONE이면 개설자에게 완료 이벤트를 발행한다")
    void 완료되면_완료_이벤트를_발행한다() {
        RunAnalysisUseCase done = (companyId, meetingId, force) -> AnalysisOutcome.done(5);
        RecordingAnalysisEventPublisher publisher = new RecordingAnalysisEventPublisher();

        trigger(done, publisher).onMeetingCompleted(event());

        assertThat(publisher.completedEvents).singleElement().satisfies(e -> {
            assertThat(e.companyId()).isEqualTo(COMPANY);
            assertThat(e.meetingId()).isEqualTo(MEETING);
            assertThat(e.hostMemberId()).isEqualTo(HOST);
            assertThat(e.title()).isEqualTo(TITLE);
            assertThat(e.topicCount()).isEqualTo(5);
        });
        assertThat(publisher.failedEvents).isEmpty();
    }

    /* FAILED면 개설자에게 실패 이벤트가 발행되는지 검증한다. */
    @Test
    @DisplayName("분석이 FAILED면 개설자에게 실패 이벤트를 발행한다")
    void 실패하면_실패_이벤트를_발행한다() {
        RunAnalysisUseCase failed = (companyId, meetingId, force) ->
                AnalysisOutcome.failed(LayerName.L4, "L4_TIMEOUT", "모델 호출 시간 초과", true);
        RecordingAnalysisEventPublisher publisher = new RecordingAnalysisEventPublisher();

        trigger(failed, publisher).onMeetingCompleted(event());

        assertThat(publisher.failedEvents).singleElement().satisfies(e -> {
            assertThat(e.companyId()).isEqualTo(COMPANY);
            assertThat(e.meetingId()).isEqualTo(MEETING);
            assertThat(e.hostMemberId()).isEqualTo(HOST);
            assertThat(e.title()).isEqualTo(TITLE);
            assertThat(e.errorCode()).isEqualTo("L4_TIMEOUT");
        });
        assertThat(publisher.completedEvents).isEmpty();
    }

    /* SKIPPED·ALREADY_RUNNING·SUPERSEDED는 사용자 관점에서 끝난 게 아니므로 알림을 안 보낸다. */
    @Test
    @DisplayName("SKIPPED·ALREADY_RUNNING·SUPERSEDED는 완료·실패 이벤트를 발행하지 않는다")
    void 중간_상태는_알림을_보내지_않는다() {
        RecordingAnalysisEventPublisher publisher = new RecordingAnalysisEventPublisher();

        trigger((companyId, meetingId, force) -> AnalysisOutcome.skipped("발화 0건"), publisher)
                .onMeetingCompleted(event());
        trigger((companyId, meetingId, force) -> AnalysisOutcome.alreadyRunning(LayerName.L3), publisher)
                .onMeetingCompleted(event());
        trigger((companyId, meetingId, force) -> AnalysisOutcome.superseded(LayerName.L3), publisher)
                .onMeetingCompleted(event());

        assertThat(publisher.completedEvents).isEmpty();
        assertThat(publisher.failedEvents).isEmpty();
    }

    /* 제목·개설자를 못 읽으면 이벤트 발행 자체를 건너뛰는지 검증한다. */
    @Test
    @DisplayName("회의 제목이나 개설자를 못 읽으면 알림을 건너뛴다")
    void 제목이나_개설자를_못읽으면_알림을_건너뛴다() {
        RunAnalysisUseCase done = (companyId, meetingId, force) -> AnalysisOutcome.done(1);
        RecordingAnalysisEventPublisher publisher = new RecordingAnalysisEventPublisher();

        new MeetingCompletedAnalysisTrigger(done, meetingId -> Optional.of(Duration.ofMinutes(42)),
                DEFAULT_COMPANY_PROVIDER,
                meetingId -> Optional.empty(), DEFAULT_HOST_PROVIDER, publisher)
                .onMeetingCompleted(event());

        assertThat(publisher.completedEvents).isEmpty();
    }

    /* 알림 발행이 터져도 분석 자체는 이미 끝난 뒤라 예외가 밖으로 나가면 안 된다. */
    @Test
    @DisplayName("알림 발행이 실패해도 예외를 밖으로 내지 않는다")
    void 알림_발행_실패가_밖으로_나가지_않는다() {
        RunAnalysisUseCase done = (companyId, meetingId, force) -> AnalysisOutcome.done(1);
        AnalysisEventPublisher exploding = new AnalysisEventPublisher() {
            @Override
            public void publish(AnalysisCompletedEvent event) {
                throw new IllegalStateException("Redis 연결 실패");
            }

            @Override
            public void publish(AnalysisFailedEvent event) {
                throw new IllegalStateException("Redis 연결 실패");
            }
        };

        assertThatCode(() -> new MeetingCompletedAnalysisTrigger(
                done, meetingId -> Optional.of(Duration.ofMinutes(42)),
                DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, exploding)
                .onMeetingCompleted(event()))
                .doesNotThrowAnyException();
    }

    private MeetingCompletedAnalysisTrigger trigger(RunAnalysisUseCase analysis, Duration length) {
        return new MeetingCompletedAnalysisTrigger(analysis, meetingId -> Optional.of(length),
                DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, new RecordingAnalysisEventPublisher());
    }

    private MeetingCompletedAnalysisTrigger trigger(RunAnalysisUseCase analysis, AnalysisEventPublisher publisher) {
        return new MeetingCompletedAnalysisTrigger(analysis, meetingId -> Optional.of(Duration.ofMinutes(42)),
                DEFAULT_COMPANY_PROVIDER,
                DEFAULT_TITLE_PROVIDER, DEFAULT_HOST_PROVIDER, publisher);
    }

    /* 종료 시각은 고정값이다 — 트리거가 이 값을 판정에 쓰지 않으므로 흐르는 시간일 이유가 없다. */
    private MeetingCompletionRequestedEvent event() {
        return new MeetingCompletionRequestedEvent(COMPANY, MEETING, 900L, COMPLETED_AT);
    }

    /* 무엇을 실어 불렀는지가 검증 대상이다 — 특히 force 다. */
    private static final class RecordingRunAnalysis implements RunAnalysisUseCase {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public AnalysisOutcome run(long companyId, long meetingId, boolean force) {
            calls.add(new Call(companyId, meetingId, force));
            return AnalysisOutcome.done(3);
        }

        private record Call(long companyId, long meetingId, boolean force) {
        }
    }

    /* 발행된 완료·실패 이벤트를 기록하는 Publisher 대역이다. */
    private static final class RecordingAnalysisEventPublisher implements AnalysisEventPublisher {

        private final List<AnalysisCompletedEvent> completedEvents = new ArrayList<>();
        private final List<AnalysisFailedEvent> failedEvents = new ArrayList<>();

        @Override
        public void publish(AnalysisCompletedEvent event) {
            completedEvents.add(event);
        }

        @Override
        public void publish(AnalysisFailedEvent event) {
            failedEvents.add(event);
        }
    }
}
