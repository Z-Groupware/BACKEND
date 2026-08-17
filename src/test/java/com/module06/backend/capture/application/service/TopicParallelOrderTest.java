package com.module06.backend.capture.application.service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.domain.model.TopicSegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주제 단위 계층의 병렬 실행 — <b>순서 보존</b>과 <b>예외 원형</b>.
 *
 * <p>이 둘이 이 병렬화에서 가장 깨지기 쉬운 자리다.
 *
 * <p><b>순서</b> — L3 는 결과 순서가 곧 저장 순서이고 회의록의 안건 차례이며, L4 는 그 순서가
 * tuple 의 sortOrder 다. 완료 순서로 모으면 <b>같은 회의를 다시 분석할 때마다 목록이 뒤섞인다.</b>
 * 그건 터지지 않고 화면만 이상해지는 종류라 테스트가 없으면 오래 안 드러난다.
 *
 * <p><b>예외</b> — CompletableFuture 는 실패를 CompletionException 으로 싸서 던진다. 그대로
 * 올리면 runOrReuse 의 오류 분류(제공자 오류 코드·재시도 가능 여부)가 전부 "알 수 없는 오류"로
 * 떨어지고, 재시도할 수 있는 실패가 영구 실패로 기록된다.
 */
class TopicParallelOrderTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("먼저 끝난 주제가 앞에 오지 않는다 — 완료 순서가 뒤집혀도 주제 순서로 돌려준다")
    void 완료_순서가_뒤집혀도_주제_순서를_지킨다() throws Exception {
        /*
         * 1번 주제를 2번이 끝날 때까지 붙잡는다 — 완료 순서를 확실히 뒤집는다.
         * 지연(sleep)으로 흉내내면 느린 기계에서 순서가 우연히 맞아 통과할 수 있다.
         */
        CountDownLatch secondDone = new CountDownLatch(1);

        List<String> results = AnalysisOrchestrator.inTopicOrder(
                List.of(topic(1, "첫째 안건"), topic(2, "둘째 안건")),
                executor,
                segment -> {
                    if (segment.topicSeq() == 1) {
                        await(secondDone);
                    } else {
                        secondDone.countDown();
                    }
                    return segment.topic();
                });

        assertThat(results).containsExactly("첫째 안건", "둘째 안건");
    }

    @Test
    @DisplayName("주제가 하나면 실행기를 쓰지 않는다 — 스레드를 빌릴 이유가 없다")
    void 주제가_하나면_그_자리에서_돈다() {
        String caller = Thread.currentThread().getName();

        List<String> results = AnalysisOrchestrator.inTopicOrder(
                List.of(topic(1, "유일한 안건")), executor,
                segment -> Thread.currentThread().getName());

        assertThat(results).containsExactly(caller);
    }

    @Test
    @DisplayName("⚠ 예외를 CompletionException 으로 싸지 않는다 — 오류 분류가 통째로 무너진다")
    void 예외를_감싸지_않고_원형으로_올린다() {
        IllegalStateException thrown = new IllegalStateException("제공자가 거절했다");

        assertThatThrownBy(() -> AnalysisOrchestrator.inTopicOrder(
                List.of(topic(1, "첫째"), topic(2, "둘째")), executor,
                segment -> {
                    if (segment.topicSeq() == 2) {
                        throw thrown;
                    }
                    return segment.topic();
                }))
                // 감싸면 호출자가 오류 코드를 못 읽고 재시도 가능 여부도 잃는다.
                .isSameAs(thrown);
    }

    @Test
    @DisplayName("주제가 없으면 아무것도 부르지 않는다")
    void 주제가_없으면_빈_목록이다() {
        assertThat(AnalysisOrchestrator.inTopicOrder(List.<TopicSegment>of(), executor,
                segment -> "부르면 안 된다")).isEmpty();
    }

    private static TopicSegment topic(int seq, String name) {
        return new TopicSegment(seq, name, 1L, 2L, List.of(1L, 2L));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("다른 주제가 끝나기를 기다리다 시간이 지났다 — 병렬로 안 돌고 있다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
