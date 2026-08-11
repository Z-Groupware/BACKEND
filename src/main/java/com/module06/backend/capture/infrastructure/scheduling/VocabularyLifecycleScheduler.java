package com.module06.backend.capture.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.service.VocabularyLifecycleService;

/*
 * 커스텀 어휘의 완료 확인과 정리를 주기적으로 돈다.
 *
 * <h2>왜 폴링인가 — 콜백이 없다</h2>
 * Transcribe 는 어휘 생성 완료를 알려주지 않는다. STT 잡 결과와 같은 사정이다.
 *
 * <h2>주기를 60초로 둔다 — STT 폴링(15초)보다 길다</h2>
 * 어휘 생성은 분 단위이고(명세 STT-01 "몇 분 걸린다"), **어휘가 늦어도 녹음은 시작할 수 있다.**
 * 그래서 초 단위로 물어볼 이유가 없다 — 도는 중인 어휘를 계속 조회하는 호출만 늘어난다.
 * 반대로 STT 결과는 회의 종료 후 대기가 곧 "요약이 언제 나오나"라 15초다.
 *
 * <h2>@EnableScheduling 을 여기 붙이지 않는다</h2>
 * TupleVectorSyncScheduler 가 이미 붙였다. 켜는 자리가 여러 개가 되면 한쪽을 지울 때 다른
 * 워커까지 조용히 멈춘다.
 *
 * <h2>한 주기에 둘을 순서대로 한다</h2>
 * 승격이 먼저다 — 승격이 이전 리소스 삭제를 만들고, 그게 정리가 볼 대상을 줄인다. 순서를
 * 뒤집으면 방금 승격된 회의의 이전 리소스가 한 주기 더 계정 상한을 쓴다.
 *
 * ⚠ 인스턴스가 여럿이면 각자 돈다. 승격은 조건부라(대기 이름이 있는 PENDING 만) 중복 승격까지는
 * 막히지만, **같은 리소스에 삭제가 두 번 나갈 수 있다** — 없는 이름 삭제는 성공으로 보므로
 * 해롭지 않다. 지금은 단일 인스턴스다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "capture.vocabulary-lifecycle.enabled", havingValue = "true",
        matchIfMissing = true)
public class VocabularyLifecycleScheduler {

    private static final long INTERVAL_MS = 60_000L;

    /* 부팅 직후는 건너뛴다. 컨텍스트가 뜨는 중에 외부 호출을 걸면 기동 로그가 실패로 덮인다. */
    private static final long INITIAL_DELAY_MS = 40_000L;

    private final VocabularyLifecycleService vocabularyLifecycleService;

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void run() {
        /*
         * 서비스가 항목별로 예외를 삼키지만 여기서도 한 번 더 잡는다. 스케줄러가 예외를 받으면
         * **그 작업이 다음 주기부터 아예 안 돈다** — 어휘가 영원히 PENDING 이거나 계정 상한이
         * 조용히 차오르는 상태가 굳는다.
         */
        try {
            int promoted = vocabularyLifecycleService.promoteReadyOnce();
            if (promoted > 0) {
                log.info("커스텀 어휘 완료 확인 — {}건 반영", promoted);
            }
        } catch (RuntimeException e) {
            log.error("어휘 완료 확인 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }

        /*
         * 멈춘 빌드를 포기한다. 승격 **뒤**에 하는 이유 — 이번 주기에 막 READY 가 된 빌드를
         * 시각만 보고 포기하면 방금 만들어진 어휘를 버린다.
         */
        try {
            int abandoned = vocabularyLifecycleService.abandonStuckOnce();
            if (abandoned > 0) {
                log.warn("커스텀 어휘 포기 — {}건. 제공자가 응답하지 않았다", abandoned);
            }
        } catch (RuntimeException e) {
            log.error("어휘 포기 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }

        try {
            int cleaned = vocabularyLifecycleService.cleanupOnce();
            if (cleaned > 0) {
                log.info("커스텀 어휘 정리 — {}건 삭제", cleaned);
            }
        } catch (RuntimeException e) {
            log.error("어휘 정리 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }

        /*
         * 밀려난 리소스를 지운다. 활성 정리와 나눠 부르는 이유 — 밀려난 것은 회의가 끝나기
         * 전에도 나오므로(재생성) 대상이 되는 시점이 다르다.
         */
        try {
            int cleanedStale = vocabularyLifecycleService.cleanupStaleOnce();
            if (cleanedStale > 0) {
                log.info("밀려난 어휘 정리 — {}건 삭제", cleanedStale);
            }
        } catch (RuntimeException e) {
            log.error("밀려난 어휘 정리 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }
    }
}
