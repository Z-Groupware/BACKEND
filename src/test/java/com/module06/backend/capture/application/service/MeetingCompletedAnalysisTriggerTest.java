package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
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
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 6, 15, 2, 40);

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
    @DisplayName("3분 미만 회의는 부르지 않는다 — 자동 트리거의 비용 관문이다")
    void 너무_짧은_회의는_분석하지_않는다() {
        RecordingRunAnalysis analysis = new RecordingRunAnalysis();

        trigger(analysis, Duration.ofSeconds(150)).onMeetingCompleted(event());

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
        new MeetingCompletedAnalysisTrigger(analysis, meetingId -> Optional.empty())
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

        new MeetingCompletedAnalysisTrigger(analysis, exploding).onMeetingCompleted(event());

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
        assertThatCode(() -> new MeetingCompletedAnalysisTrigger(
                failing, meetingId -> Optional.of(Duration.ofMinutes(42)))
                .onMeetingCompleted(event()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("분석이 터져도 회의 종료를 되돌리지 않는다 — 예외를 밖으로 내지 않는다")
    void 분석_실패가_밖으로_나가지_않는다() {
        RunAnalysisUseCase exploding = (companyId, meetingId, force) -> {
            throw new IllegalStateException("계층 호출이 터졌다");
        };

        assertThatCode(() -> new MeetingCompletedAnalysisTrigger(
                exploding, meetingId -> Optional.of(Duration.ofMinutes(42)))
                .onMeetingCompleted(event()))
                .doesNotThrowAnyException();
    }

    private MeetingCompletedAnalysisTrigger trigger(RunAnalysisUseCase analysis, Duration length) {
        return new MeetingCompletedAnalysisTrigger(analysis, meetingId -> Optional.of(length));
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
}
