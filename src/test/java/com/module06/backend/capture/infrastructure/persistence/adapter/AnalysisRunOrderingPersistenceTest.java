package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LockResult;
import com.module06.backend.capture.application.port.out.AnalysisRunRepository;
import com.module06.backend.capture.domain.model.LayerName;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * #134 · 같은 회의의 분석 실행 순서를 실제 트랜잭션·행 잠금 위에서 검증한다.
 *
 * 이 수정의 주장은 전부 **DB 가 지켜주는 것**이다 — 번호 발급이 원자적인가, 잠금이 그 번호를
 * 같은 트랜잭션에서 보는가. 가짜 저장소로는 그 주장을 검증할 수 없어서(가짜는 우리가 맞다고
 * 믿는 대로 동작한다) 여기서는 실물 어댑터를 쓴다.
 *
 * ⚠ H2 다. 행 잠금 의미가 MySQL 과 완전히 같지는 않으므로, "동시 실행이 서로 다른 번호를
 * 받는다"·"같은 계층은 하나만 잡는다"까지만 확인하고 그 이상의 잠금 세부는 주장하지 않는다.
 *
 * ⚠ **마이그레이션을 검증하는 테스트가 아니다.** 테스트 스키마는 Hibernate create-drop 이
 * 소유하고(테스트 application.yaml · Flyway 는 꺼져 있다), V5.16 DDL 이 실제로 도는지는
 * CI 의 「Schema Migration Validation (real MySQL)」 잡이 본다. 여기서 Flyway 를 켜면
 * MySQL 전용 DDL(ENGINE=InnoDB · ENUM 등)이 H2 에서 깨진다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anlzrundb;MODE=MySQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
})
@DisplayName("#134 분석 실행 순서 — 실행 번호와 계층 잠금")
class AnalysisRunOrderingPersistenceTest {

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Autowired
    private AnalysisLayerRepository analysisLayerRepository;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("실행을 시작할 때마다 번호가 오른다 — 나중에 시작한 쪽이 큰 번호를 갖는다")
    void 실행_번호는_시작할_때마다_오른다() {
        long meetingId = 9_101L;

        assertThat(analysisRunRepository.begin(meetingId)).hasValue(1L);
        assertThat(analysisRunRepository.begin(meetingId)).hasValue(2L);
        assertThat(analysisRunRepository.begin(meetingId)).hasValue(3L);
    }

    @Test
    @DisplayName("회의마다 따로 센다 — 다른 회의의 실행이 이 회의의 순서를 밀지 않는다")
    void 실행_번호는_회의_스코프다() {
        assertThat(analysisRunRepository.begin(9_102L)).hasValue(1L);
        assertThat(analysisRunRepository.begin(9_103L)).hasValue(1L);
        assertThat(analysisRunRepository.begin(9_102L)).hasValue(2L);
    }

    @Test
    @DisplayName("밀린 실행은 계층을 잡지 못한다 — 오래된 실행이 최신 결과를 덮는 경로가 여기서 끊긴다")
    void 밀린_번호로는_계층을_잡지_못한다() {
        long meetingId = 9_104L;

        long first = analysisRunRepository.begin(meetingId).orElseThrow();
        // 실행 A 가 발화를 읽고 멈춰 있는 사이에 실행 B 가 시작했다.
        long second = analysisRunRepository.begin(meetingId).orElseThrow();

        assertThat(analysisLayerRepository.tryLock(meetingId, LayerName.L1, first).result())
                .isEqualTo(LockResult.SUPERSEDED);
        // 최신 실행은 그대로 잡는다 — 조이는 방향으로만 막는다.
        assertThat(analysisLayerRepository.tryLock(meetingId, LayerName.L1, second).result())
                .isEqualTo(LockResult.ACQUIRED);
    }

    @Test
    @DisplayName("잡지 못한 밀린 실행은 계층 상태를 건드리지 않는다 — RUNNING 으로 만들어두면 최신 실행이 막힌다")
    void 밀린_실행은_계층_상태를_남기지_않는다() {
        long meetingId = 9_105L;

        long stale = analysisRunRepository.begin(meetingId).orElseThrow();
        long current = analysisRunRepository.begin(meetingId).orElseThrow();

        analysisLayerRepository.tryLock(meetingId, LayerName.L2, stale);

        // 밀린 시도가 행을 만들었다면 최신 실행이 ALREADY_RUNNING 으로 튕긴다.
        assertThat(analysisLayerRepository.findStates(meetingId)).isEmpty();
        assertThat(analysisLayerRepository.tryLock(meetingId, LayerName.L2, current).result())
                .isEqualTo(LockResult.ACQUIRED);
    }

    @Test
    @DisplayName("최신 실행이라도 이미 RUNNING 인 계층은 못 잡는다 — 중복 방어는 그대로다")
    void 실행_중인_계층은_최신_실행도_잡지_못한다() {
        long meetingId = 9_106L;

        long first = analysisRunRepository.begin(meetingId).orElseThrow();
        assertThat(analysisLayerRepository.tryLock(meetingId, LayerName.L3, first).result())
                .isEqualTo(LockResult.ACQUIRED);

        long second = analysisRunRepository.begin(meetingId).orElseThrow();
        assertThat(analysisLayerRepository.tryLock(meetingId, LayerName.L3, second).result())
                .isEqualTo(LockResult.ALREADY_RUNNING);
    }

    @Test
    @DisplayName("같은 계층을 동시에 잡으면 하나만 성공한다 — INSERT 경합이 잠금 실패로 바뀐다")
    void 없는_계층을_동시에_잡으면_하나만_성공한다() throws Exception {
        long meetingId = 9_108L;
        long runSeq = analysisRunRepository.begin(meetingId).orElseThrow();
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        /*
         * 계층 행이 **아직 없는** 상태에서 둘이 동시에 들어간다. 둘 다 "행이 없다"를 보고
         * INSERT 로 가는 경로이고, 진 쪽은 UNIQUE(meeting_id, layer) 에 걸린다.
         *
         * 그 충돌은 **커밋 시점에** 터지므로 잠금을 잡는 트랜잭션 안에서는 잡히지 않는다.
         * 어댑터가 트랜잭션 경계 밖에서 잡아 ALREADY_RUNNING 으로 옮기는 것을 여기서 고정한다 —
         * 안 옮기면 정상적인 중복 방어가 500 으로 보고된다.
         */
        List<CompletableFuture<LockResult>> futures = List.of(
                lockWhenReleased(meetingId, runSeq, start), lockWhenReleased(meetingId, runSeq, start));

        start.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        List<LockResult> results = futures.stream().map(CompletableFuture::join).toList();

        assertThat(results).containsExactlyInAnyOrder(LockResult.ACQUIRED, LockResult.ALREADY_RUNNING);
        // 행은 하나뿐이다 — 둘 다 들어갔으면 계층 상태가 두 벌이 된다.
        assertThat(analysisLayerRepository.findStates(meetingId)).hasSize(1);
    }

    @Test
    @DisplayName("동시에 시작한 두 실행이 같은 번호를 받지 않는다 — 같으면 둘 다 자기가 최신이라고 판단한다")
    void 동시에_시작해도_번호가_겹치지_않는다() throws Exception {
        long meetingId = 9_107L;
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<OptionalLong>> futures = List.of(
                beginWhenReleased(meetingId, start), beginWhenReleased(meetingId, start));

        start.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        List<OptionalLong> issued = futures.stream().map(CompletableFuture::join).toList();
        List<Long> numbers = issued.stream().filter(OptionalLong::isPresent)
                .map(OptionalLong::getAsLong).toList();

        /*
         * 발급받은 쪽은 서로 다른 번호를 갖는다. 한쪽이 비는 것도 정상이다 —
         * 첫 행을 만드는 경합에서 진 실행은 물러난다(그쪽이 늦게 시작한 것이므로 맞는 결정이다).
         */
        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).isNotEmpty();
    }

    @Test
    @DisplayName("여러 번 반복해도 번호가 겹치지 않는다 — 한 번만 돌리면 이 경합은 놓친다")
    void 반복해도_번호가_겹치지_않는다() throws Exception {
        /*
         * 위 테스트를 여러 회의로 반복한다.
         *
         * **한 판만 돌려서는 부족하다.** 경합의 결과가 매번 갈리므로, 실제로 깨져 있어도 통과할
         * 때가 있다 — 이 테스트가 CI 에서 간헐적으로만 빨간불이던 이유이고, 그동안 "H2 가 원인인
         * 플레이키"로 읽혔다. 실제 원인은 save 가 merge 로 가 진 쪽의 INSERT 가 UPDATE 로 바뀌던
         * 것이었다(MeetingAnalysisRunJpaEntity 주석).
         *
         * 회의를 매 판 새로 잡는다. 같은 회의를 다시 쓰면 두 번째 판부터는 행이 이미 있어서
         * **행 잠금이 줄을 세우고**, 정작 검증하려는 "첫 행 경합"이 일어나지 않는다.
         */
        executor = Executors.newFixedThreadPool(2);

        for (int round = 0; round < 20; round++) {
            long meetingId = 9_200L + round;
            CountDownLatch start = new CountDownLatch(1);

            List<CompletableFuture<OptionalLong>> futures = List.of(
                    beginWhenReleased(meetingId, start), beginWhenReleased(meetingId, start));

            start.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

            List<Long> numbers = futures.stream().map(CompletableFuture::join)
                    .filter(OptionalLong::isPresent)
                    .map(OptionalLong::getAsLong)
                    .toList();

            assertThat(numbers)
                    .as("round %d — 같은 번호를 받으면 둘 다 자기가 최신이라고 판단한다", round)
                    .doesNotHaveDuplicates()
                    .isNotEmpty();
        }
    }

    private CompletableFuture<LockResult> lockWhenReleased(long meetingId, long runSeq,
                                                           CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            awaitStart(start);
            return analysisLayerRepository.tryLock(meetingId, LayerName.L4, runSeq).result();
        }, executor);
    }

    private CompletableFuture<OptionalLong> beginWhenReleased(long meetingId, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            awaitStart(start);
            return analysisRunRepository.begin(meetingId);
        }, executor);
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
