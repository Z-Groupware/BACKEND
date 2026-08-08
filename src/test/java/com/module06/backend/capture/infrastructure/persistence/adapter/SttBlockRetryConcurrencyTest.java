package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.module06.backend.capture.application.port.out.SttBlockRepository;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * STT-04 · 같은 블록에 재처리가 동시에 들어와도 **한 번만 전이한다.**
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 * 잡 이름에 재시도 횟수를 넣는 이유가 "계정 내 유일"이다. 그런데 두 요청이 같은 FAILED
 * 스냅샷을 읽으면 **같은 횟수로 같은 이름을 만들어 둘 다 제출한다** — AWS 가 두 번째를 거절하고,
 * 그건 이름에 횟수를 넣어 막으려던 바로 그 상황이다.
 *
 * 처음 고칠 때는 id 로 다시 읽어 자바에서 비교했는데, **같은 트랜잭션에서 이미 조회한 엔티티는
 * 영속성 컨텍스트에 올라와 있어 잠금을 걸어도 캐시된 스냅샷이 돌아온다** — 자기가 읽은 값과
 * 자기를 비교하는 셈이라 아무것도 막지 못했다(CodeRabbit PR #223 지적). 조건을 쿼리에 넣어
 * DB 가 판정하게 바꾼 뒤에야 성립한다.
 *
 * 가짜 저장소로는 이 주장을 검증할 수 없다(가짜는 우리가 맞다고 믿는 대로 동작한다). 실물
 * 어댑터를 **서로 다른 트랜잭션에서 동시에** 부른다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sttretrydb;MODE=MySQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
})
@DisplayName("STT-04 재처리 전이 동시성")
class SttBlockRetryConcurrencyTest {

    private static final long MEETING = 9_401L;
    private static final int BLOCK_SEQ = 3;

    @Autowired
    private SttBlockRepository sttBlockRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM stt_block");
        jdbcTemplate.update(
                "INSERT INTO stt_block "
                        + "(meeting_id, block_seq, start_offset_ms, end_offset_ms, cut_reason, "
                        + " provider, status, retry_count, audio_s3_key) "
                        + "VALUES (?, ?, 0, 600000, 'VAD_SILENCE', 'aws-transcribe', 'FAILED', 2, ?)",
                MEETING, BLOCK_SEQ, "meeting-9401/blocks/3.wav");
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("동시에 두 번 재처리해도 한 쪽만 전이한다 — 둘 다 되면 같은 잡 이름이 두 번 나간다")
    void 동시_재처리는_하나만_전이한다() throws Exception {
        long blockId = blockId();
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        /*
         * 둘 다 **같은 스냅샷(retryCount=2)** 을 읽은 상황이다. 실제 경로에서 서비스가 조회 뒤
         * 이 값을 그대로 넘긴다 — 그래서 여기서도 같은 기대값으로 부른다.
         */
        List<CompletableFuture<Boolean>> futures = List.of(
                claimWhenReleased(blockId, start), claimWhenReleased(blockId, start));

        start.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        List<Boolean> results = futures.stream().map(CompletableFuture::join).toList();

        // 정확히 하나만 참이어야 한다. 둘 다 참이면 같은 이름의 잡이 두 번 제출된다.
        assertThat(results).containsExactlyInAnyOrder(true, false);
        // 시도 횟수도 한 번만 올라야 한다.
        assertThat(retryCount()).isEqualTo(3);
        assertThat(status()).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("이미 QUEUED 인 블록은 전이하지 않는다 — 실패한 블록만 다시 돌린다")
    void 실패가_아니면_전이하지_않는다() {
        jdbcTemplate.update("UPDATE stt_block SET status = 'QUEUED' WHERE meeting_id = ?", MEETING);

        assertThat(sttBlockRepository.markQueuedForRetry(blockId(), 2, "aws-transcribe", "job-x")).isFalse();
    }

    @Test
    @DisplayName("읽은 시도 횟수가 달라졌으면 전이하지 않는다 — 한 바퀴 돈 뒤의 요청이 같은 이름을 만든다")
    void 시도_횟수가_다르면_전이하지_않는다() {
        // 그 사이 누가 재처리를 한 번 돌려 횟수가 3 이 됐다.
        jdbcTemplate.update("UPDATE stt_block SET retry_count = 3 WHERE meeting_id = ?", MEETING);

        assertThat(sttBlockRepository.markQueuedForRetry(blockId(), 2, "aws-transcribe", "job-x")).isFalse();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private CompletableFuture<Boolean> claimWhenReleased(long blockId, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            // 어댑터가 @Transactional 이라 스레드마다 별도 트랜잭션에서 돈다.
            return sttBlockRepository.markQueuedForRetry(
                    blockId, 2, "aws-transcribe", "meeting-9401-block-3-r3");
        }, executor);
    }

    private long blockId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM stt_block WHERE meeting_id = ? AND block_seq = ?",
                Long.class, MEETING, BLOCK_SEQ);
    }

    private int retryCount() {
        return jdbcTemplate.queryForObject(
                "SELECT retry_count FROM stt_block WHERE meeting_id = ?", Integer.class, MEETING);
    }

    private String status() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM stt_block WHERE meeting_id = ?", String.class, MEETING);
    }
}
