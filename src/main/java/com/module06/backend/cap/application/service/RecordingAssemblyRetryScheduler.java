package com.module06.backend.cap.application.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.service.PendingAssemblyRetryRegistry.PendingRetry;

/*
 * 디스크 용량 부족으로 미뤄둔 조립을 주기적으로 다시 시도한다(PendingAssemblyRetryRegistry 참고).
 *
 * <h2>@EnableScheduling을 여기 붙이지 않는다</h2>
 * capture의 TupleVectorSyncScheduler/notification의 MeetingReminderScheduler가 이미 붙였다
 * (SttResultPollingScheduler와 동일한 이유 — 스케줄링을 켜는 자리가 여러 곳이면 한쪽을 지울 때
 * 다른 워커까지 조용히 멈춘다).
 *
 * <h2>주기·상한을 3분/3회로 둔다</h2>
 * TODO: FFMPEG_TIMEOUT(RecordingAssemblyS3FfmpegAdapter, 10분)처럼 실측 없이 정한 값이다.
 * "동시에 돌던 다른 조립이 끝나기를 기다린다"는 취지라, 그 조립이 최악의 경우 걸릴 수 있는
 * 시간(FFMPEG_TIMEOUT 한 사이클)만큼만 커버하고 그 이상은 "기다리면 되는 상황"이 아니라
 * 디스크 자체가 구조적으로 부족한 상황으로 본다 — 그때는 재시도를 멈추고 사람이 CAP-05로
 * 직접 다시 부르거나 디스크를 늘려야 한다.
 */
@Component
@ConditionalOnProperty(name = "cap.assembly-retry.enabled", havingValue = "true", matchIfMissing = true)
public class RecordingAssemblyRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordingAssemblyRetryScheduler.class);

    private static final long INTERVAL_MS = 3 * 60 * 1000L;
    /* 부팅 직후는 건너뛴다 — 컨텍스트가 뜨는 중에 재시도를 걸면 기동 로그가 실패로 덮인다
       (SttResultPollingScheduler와 동일 이유). */
    private static final long INITIAL_DELAY_MS = 60_000L;
    private static final int MAX_ATTEMPTS = 3;

    private final PendingAssemblyRetryRegistry registry;
    private final RecordingAssemblyDispatcher recordingAssemblyDispatcher;

    RecordingAssemblyRetryScheduler(PendingAssemblyRetryRegistry registry,
                                    RecordingAssemblyDispatcher recordingAssemblyDispatcher) {
        this.registry = registry;
        this.recordingAssemblyDispatcher = recordingAssemblyDispatcher;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    void retryPending() {
        /*
         * 서비스가 개별 재시도(dispatch)를 이미 예외로 삼키지만(RecordingAssemblyS3FfmpegAdapter의
         * outer catch) 여기서도 한 번 더 잡는다. 스케줄러가 예외를 받으면 그 작업이 다음
         * 주기부터 아예 안 돈다 — 대기 중인 다른 회의까지 영원히 재시도가 안 되는 상태가
         * 조용히 굳는다(SttResultPollingScheduler와 동일 이유).
         */
        try {
            for (Map.Entry<Long, PendingRetry> entry : registry.snapshot().entrySet()) {
                retryOne(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException e) {
            log.error("조립 재시도 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }
    }

    private void retryOne(Long meetingId, PendingRetry retry) {
        if (retry.inFlight()) {
            // 이전 주기에 디스패치한 재시도가 아직 안 끝났다(조립은 최대 FFMPEG_TIMEOUT까지
            // 걸릴 수 있어 이 스케줄러 주기보다 길 수 있다) — 또 디스패치하면 같은 회의를
            // 동시에 두 번 조립하게 돼 디스크 경합을 오히려 키운다. 이번 주기는 건너뛴다.
            return;
        }
        if (retry.attempts() >= MAX_ATTEMPTS) {
            log.error("디스크 용량 부족 재시도 {}회 소진 — 포기한다. 사람이 CAP-05로 직접 재시도해야 "
                    + "한다(또는 디스크 여유 확인). meetingId={}", MAX_ATTEMPTS, meetingId);
            registry.clear(meetingId);
            return;
        }
        if (!registry.tryMarkInFlight(meetingId)) {
            // 그 사이 성공해서(다른 스레드가 clear) 대기 목록에서 이미 빠졌거나, 방금 다른
            // 경합으로 진행 중 표시가 붙었다 — 어느 쪽이든 이번 주기에 할 일이 없다.
            return;
        }
        log.info("디스크 용량 부족으로 미뤄둔 조립 재시도 — meetingId={} 시도={}/{}",
                meetingId, retry.attempts(), MAX_ATTEMPTS);
        try {
            recordingAssemblyDispatcher.dispatch(meetingId, retry.lastSegmentSeq(), retry.lastSeq());
        } catch (RuntimeException e) {
            // dispatch(@Async) 제출 자체가 실패했다(예: 비동기 풀 포화로 태스크 거부) — 실제 조립은
            // 시작도 안 됐으니 방금 tryMarkInFlight로 세운 진행 중 표시를 여기서 풀어야 다음
            // 주기에 다시 시도된다(CodeRabbit 지적 — 안 풀면 영원히 "진행 중"으로 오판돼 stuck).
            log.error("조립 재시도 디스패치 자체가 실패 — meetingId={}", meetingId, e);
            registry.releaseInFlight(meetingId);
        }
    }
}
