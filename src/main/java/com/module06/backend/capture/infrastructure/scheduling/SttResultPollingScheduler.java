package com.module06.backend.capture.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.service.SttResultPollingService;

/*
 * 제출해 둔 STT 잡의 결과를 주기적으로 가져온다.
 *
 * <h2>왜 폴링인가 — 콜백이 없다</h2>
 * AWS Transcribe 는 완료를 HTTP 로 알려주지 않는다. EventBridge → SQS/Lambda 로 받을 수는
 * 있지만 이 저장소에는 큐가 아직 없고(회의 종료 자동 분석도 큐 대신 전용 스레드 풀을 쓴다),
 * 큐를 들이는 것은 이 작업보다 큰 결정이다.
 *
 * <h2>@EnableScheduling 을 여기 붙이지 않는다</h2>
 * TupleVectorSyncScheduler 가 이미 붙였다. 두 곳에 붙여도 동작은 같지만, 스케줄링을 켜는 자리가
 * 두 개가 되면 한쪽을 지울 때 다른 워커까지 조용히 멈춘다.
 *
 * <h2>주기를 15초로 둔다</h2>
 * 블록 하나는 10분 오디오이고 Transcribe 는 대개 실시간의 몇 분의 일로 끝낸다 — 분 단위로 두면
 * 회의 종료 후 대기가 그만큼 늘고, 그 대기가 곧 "요약이 언제 나오나"다. 반대로 초 단위로 더
 * 줄이면 도는 중인 잡을 계속 물어보는 호출만 늘어난다(상태 전이가 없는 조회다).
 *
 * <h2>fixedDelay 다</h2>
 * 한 주기가 길어지면 다음 주기가 겹쳐 시작하고, 그러면 같은 블록을 두 워커가 집어 **같은 결과를
 * 두 번 적재한다.** fixedDelay 는 끝난 뒤부터 세므로 그 겹침이 없다.
 *
 * ⚠ 인스턴스가 여럿이면 이 방어는 성립하지 않는다(각자 자기 주기를 돈다). 지금은 단일
 * 인스턴스다. 늘릴 때는 상태 전이가 조건부라(markDone 이 QUEUED·RUNNING 에서만 옮긴다) 중복
 * 적재까지는 막히지만, **같은 결과를 두 번 읽는 S3 호출은 두 번 나간다.**
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "capture.stt-polling.enabled", havingValue = "true", matchIfMissing = true)
public class SttResultPollingScheduler {

    private static final long INTERVAL_MS = 15_000L;

    /* 부팅 직후는 건너뛴다. 컨텍스트가 뜨는 중에 외부 호출을 걸면 기동 로그가 실패로 덮인다. */
    private static final long INITIAL_DELAY_MS = 20_000L;

    private final SttResultPollingService sttResultPollingService;

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void poll() {
        /*
         * 서비스가 블록별로 예외를 삼키지만 여기서도 한 번 더 잡는다. 스케줄러가 예외를 받으면
         * **그 작업이 다음 주기부터 아예 안 돈다** — 받아쓰기 결과가 영원히 안 들어오는 상태가
         * 조용히 굳는다(TupleVectorSyncScheduler 와 같은 이유).
         */
        try {
            int settled = sttResultPollingService.pollOnce();
            if (settled > 0) {
                log.info("STT 결과 반영 — {}개 블록 끝맺음", settled);
            }
        } catch (RuntimeException e) {
            log.error("STT 폴링 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }
    }
}
