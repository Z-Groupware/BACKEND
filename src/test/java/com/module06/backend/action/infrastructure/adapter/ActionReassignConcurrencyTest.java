package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.model.AssigneeSource;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.handover.application.port.out.ActionReassignPort;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * ActionReassignAdapter.reassign()의 read-modify-write 경합을 실 DB 위에서 검증한다(#126
 * 코드래빗 지적, 2026-08-08 정리). SELECT ... FOR UPDATE 없이는 동시에 들어온 두 재배정
 * 요청이 서로의 담당자 일치 검사를 통과해버려 나중에 커밋한 쪽이 조용히 이긴다 —
 * "담당자가 이미 바뀌었다"는 신호가 사라진다.
 */
@SpringBootTest
class ActionReassignConcurrencyTest {

    @Autowired
    private ActionReassignPort actionReassignPort;

    @Autowired
    private ActionRepository actionRepository;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyOneOfTwoConcurrentReassignsSucceeds() throws Exception {
        Action saved = actionRepository.save(Action.create(
                1L, 100L, null, null, null, 5L,
                ActionType.PERSONAL, "액션", "설명", LocalDate.of(2026, 8, 20), false,
                AssigneeSource.EXPLICIT_CALL, null, null, false
        ));
        Long actionId = saved.getId();

        executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<Boolean>> futures = List.of(
                reassignWhenReleased(actionId, 5L, 7L, start),
                reassignWhenReleased(actionId, 5L, 9L, start));

        start.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        long succeeded = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        // 잠금이 없으면 둘 다 조용히 성공(둘 다 "담당자 일치" 검사를 통과)할 수 있다 —
        // 정확히 하나만 이겨야 한다.
        assertThat(succeeded).isEqualTo(1L);

        Long finalAssignee = actionRepository.findById(actionId).orElseThrow().getAssigneeMemberId();
        assertThat(finalAssignee).isIn(7L, 9L);
    }

    private CompletableFuture<Boolean> reassignWhenReleased(Long actionId, Long from, Long to, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            awaitStart(start);
            try {
                actionReassignPort.reassign(actionId, from, to);
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
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
