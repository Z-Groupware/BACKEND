package com.module06.backend.cap.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * 유실된 STT 트리거 복구를 주기적으로 돌린다(#574 · LostSttTriggerRecoveryService 참고).
 *
 * <h2>@EnableScheduling을 여기 붙이지 않는다</h2>
 * capture의 TupleVectorSyncScheduler·notification의 MeetingReminderScheduler가 이미 붙였다
 * (RecordingAssemblyRetryScheduler와 동일한 이유 — 스케줄링을 켜는 자리가 여러 곳이면
 * 한쪽을 지울 때 다른 워커까지 조용히 멈춘다).
 *
 * <h2>주기를 5분으로 둔다</h2>
 * 복구 대상은 유예(10분)를 지난 녹음이므로, 이보다 촘촘히 돌아도 새로 잡히는 것이 없다.
 * 반대로 너무 길게 두면 배포 직후 유실된 회의가 그만큼 오래 방치된다 — 사용자는 그 시간 동안
 * 요약이 없는 회의를 본다.
 */
@Component
@ConditionalOnProperty(name = "cap.lost-stt-trigger-recovery.enabled", havingValue = "true", matchIfMissing = true)
public class LostSttTriggerRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(LostSttTriggerRecoveryScheduler.class);

    private static final long INTERVAL_MS = 5 * 60 * 1000L;

    /* 부팅 직후는 건너뛴다 — 컨텍스트가 뜨는 중에 외부 호출을 걸면 기동 로그가 실패로 덮인다. */
    private static final long INITIAL_DELAY_MS = 90_000L;

    private final LostSttTriggerRecoveryService lostSttTriggerRecoveryService;

    LostSttTriggerRecoveryScheduler(LostSttTriggerRecoveryService lostSttTriggerRecoveryService) {
        this.lostSttTriggerRecoveryService = lostSttTriggerRecoveryService;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    void recoverLostTriggers() {
        try {
            int recovered = lostSttTriggerRecoveryService.recoverOnce();
            if (recovered > 0) {
                /*
                 * 건수를 남긴다. 이 배치가 무언가를 주웠다는 것은 **어딘가에서 트리거가
                 * 유실됐다는 뜻**이고, 그 빈도가 근본 원인(afterCommit 의존)을 고칠지 말지의
                 * 판단 근거다. 조용히 고치면 그 근거가 영영 안 쌓인다.
                 */
                log.warn("유실된 STT 트리거 {}건을 복구했다 — 트리거 유실이 실제로 일어나고 있다.", recovered);
            }
        } catch (RuntimeException e) {
            /*
             * 서비스가 건별로 예외를 삼키지만 여기서도 한 번 더 잡는다. 스케줄러가 예외를 받으면
             * **그 작업이 다음 주기부터 아예 안 돈다** — 복구 배치가 조용히 멈추면 이 이슈가
             * 고치려던 상태로 그대로 되돌아간다(RecordingAssemblyRetryScheduler와 동일 이유).
             */
            log.error("유실된 STT 트리거 복구 주기 실패 — 다음 주기에 다시 시도한다.", e);
        }
    }
}
