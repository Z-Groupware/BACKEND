package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.event.AnalysisCompletedEvent;
import com.module06.backend.capture.application.event.AnalysisFailedEvent;
import com.module06.backend.capture.application.event.SttTranscriptCompletedEvent;
import com.module06.backend.capture.application.port.out.AnalysisEventPublisher;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;

/*
 * MEET-08(회의 종료)이 발행한 이벤트를 받아 분석을 시작한다. **파이프라인의 자동 입구**다.
 *
 * 여기가 붙기 전까지 분석을 부르는 경로는 ANLZ-01(사람이 누르는 것) 하나뿐이었다 —
 * D 는 이벤트를 발행하고 있었지만 받는 쪽이 없어서, 회의를 끝내도 아무 일도 일어나지 않았다.
 *
 * <h2>왜 AFTER_COMMIT 인가</h2>
 * 회의 DONE 저장이 **커밋된 뒤에만** 분석한다. 커밋 전에 시작하면 우리는 아직 IN_PROGRESS 인
 * 회의를 읽고, 그 트랜잭션이 롤백되면 **일어나지 않은 회의 종료에 토큰을 태운 것**이 된다.
 * D 쪽 발행부도 그 전제로 쓰여 있다(SpringMeetingCompletionEventPublisher 주석).
 *
 * <h2>왜 비동기인가</h2>
 * 분석은 분 단위로 돈다(계층 9개 · 모델 호출 7회). 동기로 두면 MEET-08 의 HTTP 응답이 그만큼
 * 붙잡히는데, 그 API 는 **"분석 완료를 기다리지 않는다"**를 계약으로 내놨다. 큐가 없는 지금은
 * 전용 스레드 풀이 그 자리를 대신한다(AnalysisAsyncConfig).
 *
 * <h2>D 를 부르지 않는다 · D 도 우리를 부르지 않는다</h2>
 * 이 클래스는 이벤트 레코드 하나만 안다. D 는 A 의 유스케이스를 직접 부르지 않는 것이 계약이고
 * (MeetingCompletionService 주석), 그래서 한쪽을 고쳐도 다른 쪽이 컴파일에서 깨지지 않는다.
 *
 * <h2>여기서 던지지 않는다</h2>
 * 예외를 그대로 올리면 갈 곳이 없다. AFTER_COMMIT 리스너의 예외는 이미 커밋된 트랜잭션을
 * 되돌리지 못하고, 비동기 스레드에서는 요청자에게 닿지도 않는다 — 아무도 못 보는 스택 트레이스
 * 하나가 남을 뿐이다. 실패는 로그와 analysis_layer 상태로 남기고, 사람이 ANLZ-01 로 다시 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingCompletedAnalysisTrigger {

    /*
     * 자동 실행 하한이다. 이보다 짧은 회의는 부르지 않는다(명세 ANLZ-01 SKIPPED_TOO_SHORT).
     *
     * 자동 트리거는 비용 통제를 약하게 만든다 — 테스트로 만든 회의, 들어왔다 바로 나온 회의까지
     * 전부 모델을 태운다. 3분 미만에서 "누가·무엇을·언제까지"가 나올 일도 드물다.
     * 걸러진 회의도 사람이 원하면 ANLZ-01 로 돌릴 수 있다.
     */
    private static final Duration MIN_LENGTH_FOR_AUTO_RUN = Duration.ofMinutes(3);

    private final RunAnalysisUseCase runAnalysisUseCase;
    private final MeetingLengthProvider meetingLengthProvider;

    /* STT 완료 이벤트에는 회사 식별자가 없으므로 D 소유 회의에서 읽는다. */
    private final MeetingCompanyProvider meetingCompanyProvider;

    /* 완료·실패 알림 문구에 넣을 회의 제목을 읽는 D 소유 데이터 조회 Port다. */
    private final MeetingTitleProvider meetingTitleProvider;

    /* 알림 수신자(회의 개설자)를 읽는 D 소유 데이터 조회 Port다. */
    private final MeetingHostProvider meetingHostProvider;

    /* 완료·실패 신호를 알림 도메인에 전달하는 아웃바운드 Port다. */
    private final AnalysisEventPublisher analysisEventPublisher;

    @Async("analysisTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingCompleted(MeetingCompletionRequestedEvent event) {
        runAutomatically(event.companyId(), event.meetingId());
    }

    /* 대면·비대면 모두 마지막 STT 블록이 성공한 뒤 같은 자동 분석 경로를 사용한다. */
    @Async("analysisTaskExecutor")
    @EventListener
    public void onSttTranscriptCompleted(SttTranscriptCompletedEvent event) {
        Optional<MeetingCompanyProvider.AutomaticAnalysisTarget> target =
                meetingCompanyProvider.findAutomaticAnalysisTarget(event.meetingId());
        if (target.isEmpty()) {
            log.warn("STT 완료 자동 분석 생략 — 회의 회사를 읽지 못했다. meetingId={}", event.meetingId());
            return;
        }
        if (!target.get().completed()) {
            log.info("STT 완료 자동 분석 대기 — 아직 진행 중인 회의다. meetingId={}", event.meetingId());
            return;
        }
        runAutomatically(target.get().companyId(), event.meetingId());
    }

    /* 두 자동 입구가 회사 관문·중복 방어·알림 규칙까지 완전히 같은 경로를 공유한다. */
    private void runAutomatically(long companyId, long meetingId) {

        try {
            if (tooShortForAutoRun(meetingId)) {
                return;
            }

            /*
             * force 는 false 다. 이미 완료된 회의를 자동 경로가 다시 태우면 재과금이고,
             * 강제 재실행은 별도 권한과 확인 모달을 요구하는 **사람의 판단**이다(명세 ANLZ-01).
             *
             * 유스케이스를 부른다(오케스트레이터가 아니라). 회사 스코프 관문·중복 실행 판정·
             * 참석자 조회가 거기 있고, 자동 경로만 그것들을 건너뛰면 두 입구가 서로 다른 규칙으로
             * 돌게 된다 — 그 갈림이 정확히 이 저장소가 겪은 사고다(#100).
             */
            AnalysisOutcome outcome = runAnalysisUseCase.run(companyId, meetingId, false);
            logOutcome(meetingId, outcome);
            notifyOutcome(companyId, meetingId, outcome);

        } catch (BusinessException e) {
            /*
             * "이미 진행 중"·"이미 완료"가 여기로 온다. 오류가 아니라 **중복이 걸러진 정상 동작**이다 —
             * 회의를 두 번 끝내려는 시도나, 사람이 먼저 ANLZ-01 을 눌러둔 경우다.
             */
            log.info("회의 종료 자동 분석 생략 — meetingId={} 사유={}", meetingId, e.getMessage());
        } catch (RuntimeException e) {
            // 던져봐야 갈 곳이 없다(클래스 주석). 어디서 멈췄는지는 analysis_layer 에 남는다.
            log.error("회의 종료 자동 분석 실패 — meetingId={}", meetingId, e);
        }
    }

    /*
     * 결과를 남긴다. **실패는 INFO 로 두지 않는다.**
     *
     * 자동 경로에는 결과를 보는 사람이 없다 — 사람이 버튼을 눌렀으면 화면이 실패를 보여주지만,
     * 회의 종료로 시작된 분석은 아무도 응답을 받지 않는다. 전부 INFO 로 남기면 실패한 회의가
     * 로그 더미에 묻히고, 사용자가 "요약이 왜 없냐"고 물을 때까지 아무도 모른다.
     *
     * SKIPPED 는 INFO 다. 발화 0건·이미 완료는 정상 동작이고, 그 판단의 근거는 메시지에 있다.
     */
    private void logOutcome(long meetingId, AnalysisOutcome outcome) {
        if (outcome.status() == AnalysisOutcome.Status.FAILED) {
            log.warn("회의 종료 자동 분석 실패 — meetingId={} 계층={} code={} 재시도가능={}",
                    meetingId, outcome.failedLayer(), outcome.errorCode(), outcome.retryable());
            return;
        }
        log.info("회의 종료 자동 분석 — meetingId={} status={} 주제={} {}",
                meetingId, outcome.status(), outcome.topicCount(),
                outcome.message() != null ? outcome.message() : "");
    }

    /*
     * DONE·FAILED일 때만 개설자에게 완료·실패 신호를 보낸다.
     *
     * SKIPPED·ALREADY_RUNNING·SUPERSEDED는 사용자 관점에서 "끝난 것"이 아니다 — 아직 대상이
     * 없거나(SKIPPED) 다른 실행이 대신 끝낼 것이므로(ALREADY_RUNNING·SUPERSEDED) 그 실행이
     * 끝날 때 이 메서드가 다시 불린다. 여기서 같이 알려버리면 "요약 완료" 알림이 실제로는
     * 아무 결과도 없는 회의에 뜬다.
     *
     * 알림 실패를 별도로 감싸는 이유 — logOutcome 다음 줄이라 같은 catch(RuntimeException)
     * 블록 안에 있으면, 분석은 성공했는데 알림만 실패한 경우도 "회의 종료 자동 분석 실패"로
     * 잘못 기록된다. 분석 결과와 알림 결과는 서로 다른 실패다.
     */
    private void notifyOutcome(long companyId, long meetingId, AnalysisOutcome outcome) {
        if (outcome.status() != AnalysisOutcome.Status.DONE
                && outcome.status() != AnalysisOutcome.Status.FAILED) {
            return;
        }

        try {
            Optional<String> title = meetingTitleProvider.titleOf(meetingId);
            Optional<Long> hostMemberId = meetingHostProvider.hostMemberIdOf(meetingId);
            if (title.isEmpty() || hostMemberId.isEmpty()) {
                log.warn("완료·실패 알림 생략 — 제목 또는 개설자를 읽지 못했다. meetingId={}", meetingId);
                return;
            }

            if (outcome.status() == AnalysisOutcome.Status.DONE) {
                analysisEventPublisher.publish(new AnalysisCompletedEvent(
                        companyId, meetingId, hostMemberId.get(), title.get(), outcome.topicCount()));
            } else {
                analysisEventPublisher.publish(new AnalysisFailedEvent(
                        companyId, meetingId, hostMemberId.get(), title.get(), outcome.errorCode()));
            }
        } catch (RuntimeException e) {
            // 여기서 던져도 분석 자체는 이미 끝났다 — 알림 실패로 분석 결과를 되돌릴 이유가 없다.
            log.error("완료·실패 알림 발행 실패 — meetingId={}", meetingId, e);
        }
    }

    /*
     * 실측 길이로 자동 실행 여부를 가른다.
     *
     * 길이를 **못 읽으면 돌린다.** 모르는 것과 짧은 것은 다르고, 모를 때 건너뛰면 멀쩡한 회의의
     * 분석이 조용히 사라진다 — 사람은 분석이 안 됐다는 사실조차 모른다. 반대 방향의 손해는
     * 토큰이고, 그건 로그로 보인다.
     */
    private boolean tooShortForAutoRun(long meetingId) {
        Optional<Duration> length;
        try {
            length = meetingLengthProvider.actualLengthOf(meetingId);
        } catch (RuntimeException e) {
            /*
             * 조회가 터진 것도 **길이를 모르는 것**이다. 위 규칙과 같은 방향으로 간다 —
             * 여기서 예외를 그대로 올리면 바깥 catch 가 "분석 실패"로 기록하고 분석은 시작조차
             * 되지 않는다. DB 가 잠깐 흔들렸다는 이유로 회의의 분석이 통째로 사라지는 것은
             * 하한 검사가 하려던 일이 아니다.
             */
            log.warn("회의 길이 조회가 실패해 하한 검사를 건너뛴다 — meetingId={}", meetingId, e);
            return false;
        }

        if (length.isEmpty()) {
            log.warn("회의 길이를 읽지 못해 하한 검사를 건너뛴다 — meetingId={}", meetingId);
            return false;
        }

        if (length.get().compareTo(MIN_LENGTH_FOR_AUTO_RUN) < 0) {
            log.info("회의 종료 자동 분석 생략 — {}초짜리 회의다(하한 {}분). meetingId={}",
                    length.get().toSeconds(), MIN_LENGTH_FOR_AUTO_RUN.toMinutes(), meetingId);
            return true;
        }
        return false;
    }
}
