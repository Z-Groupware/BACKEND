package com.module06.backend.capture.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.service.TupleVectorSyncService;

/*
 * 밀린 few-shot 예시를 주기적으로 Qdrant 에 올린다(AI-08).
 *
 * <h2>왜 스케줄러인가 — 확정 직후에 밀지 않는 이유</h2>
 * RVW-02 커밋 직후 이벤트로 밀 수도 있지만, 그 경로만 두면 **그때 실패한 예시를 다시 올릴
 * 방법이 없다.** 서버가 내려가 있었거나 AI 서버가 잠깐 안 뜬 경우가 정확히 그것이고, 그
 * 예시들은 영원히 인덱스에 없는 채로 남는다. 주기 워커는 그 재시도를 자기 안에 갖는다 —
 * V5.10 이 vector_synced·sync_attempts 를 둔 것이 이 워커를 전제한 설계다.
 *
 * <h2>fixedDelay 다 — fixedRate 가 아니다</h2>
 * 한 주기가 길어지면(임베딩이 느리거나 배치가 크면) 다음 주기가 겹쳐서 시작한다. 그러면 같은
 * 행을 두 워커가 집어 **같은 예시를 두 번 임베딩한다.** fixedDelay 는 끝난 뒤부터 세므로
 * 그 겹침이 생기지 않는다.
 *
 * ⚠ 인스턴스가 여럿이면 이 방어는 성립하지 않는다(각자 자기 주기를 돈다). 지금은 단일
 * 인스턴스라 두지 않았고, 늘릴 때는 행 잠금이나 리더 선출이 필요하다 — Qdrant 는 같은 포인트
 * id 로 덮어쓰므로 **중복 저장은 안 되지만 임베딩 비용은 두 번 나간다.**
 *
 * <h2>끌 수 있게 둔다</h2>
 * 테스트는 AI 서버가 없으므로 매 주기 실패 로그가 쌓인다. 로컬에서 Qdrant 없이 돌릴 때도
 * 같다. 기본값은 켜짐이다 — 운영에서 이 워커가 안 도는 것이 더 조용한 사고라서, 끄는 쪽이
 * 명시적이어야 한다.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "capture.vector-sync.enabled", havingValue = "true", matchIfMissing = true)
public class TupleVectorSyncScheduler {

    /*
     * 주기. 라벨은 사람이 검토 화면에서 하나씩 만드는 속도로 쌓이므로 초 단위로 볼 이유가 없다.
     * 반대로 너무 길게 두면 방금 고친 값이 다음 회의 분석에 안 잡힌다 — 1분이 그 사이다.
     */
    private static final long INTERVAL_MS = 60_000L;

    /* 부팅 직후는 건너뛴다. 컨텍스트가 뜨는 중에 외부 호출을 걸면 기동 로그가 실패로 덮인다. */
    private static final long INITIAL_DELAY_MS = 30_000L;

    private final TupleVectorSyncService tupleVectorSyncService;

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void sync() {
        /*
         * 서비스가 예외를 삼키지만 여기서도 한 번 더 잡는다. 스케줄러가 예외를 받으면 **그
         * 작업이 다음 주기부터 아예 안 돈다** — 예시가 안 올라가는 것이 조용히 영구화된다.
         */
        try {
            int synced = tupleVectorSyncService.syncOnce();
            if (synced > 0) {
                log.info("few-shot 예시 색인 — {}건 반영", synced);
            }
        } catch (RuntimeException e) {
            log.error("벡터 색인 워커에서 예상치 못한 오류 — 다음 주기에 다시 돈다", e);
        }
    }
}
