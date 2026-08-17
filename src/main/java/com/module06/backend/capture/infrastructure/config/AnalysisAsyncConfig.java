package com.module06.backend.capture.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/*
 * 회의 종료(MEET-08) 자동 분석이 도는 스레드 풀이다.
 *
 * <h2>왜 별도 풀인가</h2>
 * 이 작업은 **분 단위**로 돈다(계층 9개 · Gemini 호출 7회). 공용 풀에 얹으면 회의 몇 개가
 * 동시에 끝났을 때 다른 비동기 작업이 그 뒤에 줄을 선다. 이름을 붙인 풀을 따로 두면 스레드
 * 덤프에서 "지금 분석이 몇 개 도는지"가 그대로 보인다.
 *
 * <h2>큐가 아니다 — SQS 가 붙기 전까지의 자리다</h2>
 * 프로세스 안에 있으므로 **재시작하면 대기 중인 작업이 사라진다.** 그래도 안전한 이유는
 * 분석이 회의를 지우지 않기 때문이다 — 놓친 회의는 ANLZ-01 로 사람이 다시 돌릴 수 있고,
 * 계층 상태(analysis_layer)를 보면 어디까지 됐는지 남아 있다. 명세의 202·QUEUED 로 가는
 * 자리가 여기이며, 그때 이 설정은 통째로 SQS 워커로 바뀐다.
 *
 * <h2>포화되면 버린다(AbortPolicy)</h2>
 * 기본값인 AbortPolicy 를 그대로 쓴다. CallerRunsPolicy 로 두면 넘칠 때 **요청 스레드가**
 * 분석을 돌게 되어, MEET-08 응답이 몇 분씩 늦어진다 — "분석 완료를 기다리지 않는다"는
 * 그 API 의 계약이 깨진다. 거절은 로그로 보이고 사람이 ANLZ-01 로 돌리면 된다.
 */
@Configuration
@EnableAsync
public class AnalysisAsyncConfig {

    /* 동시에 도는 분석 수. 계층이 Gemini 를 부르는 동안 대기하므로 CPU 코어 수와 무관하다. */
    private static final int POOL_SIZE = 2;

    /* 대기열. 짧게 둔다 — 길게 두면 몇십 분 전에 끝난 회의가 뒤늦게 분석되는 편이 나쁘다. */
    private static final int QUEUE_CAPACITY = 20;

    @Bean("analysisTaskExecutor")
    public Executor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        /*
         * 종료 시 돌던 분석을 기다린다. 중간에 끊기면 계층 하나가 **RUNNING 인 채로 남고**,
         * 그 회의는 다음 실행이 "이미 실행 중"으로 보고 물러난다 — force 재실행도 같은 잠금에
         * 막히므로 사람이 풀 방법이 없다.
         *
         * ⚠ **이 대기는 보장이 아니다.** 회의 하나가 이 시간을 넘기면(계층 9개 · 모델 호출 7회라
         * 큰 회의는 넘을 수 있다) 그대로 끊긴다. 무한정 기다리게 두지 않는 이유는 배포가 그만큼
         * 멈추기 때문이다 — 종료를 못 하는 서버는 그 자체로 사고다.
         *
         * 그래서 남은 RUNNING 을 되찾는 경로를 따로 뒀다(#177) — 계층 잠금에 심장 박동을 찍고,
         * 멈춘 것만 다음 실행이 회수한다(LayerLiveness). 이 대기는 그 회수가 필요한 빈도를
         * 줄일 뿐이고, 없애지는 못한다.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180);
        executor.initialize();
        return executor;
    }

    /*
     * 주제 단위 계층(L3·L3.5·L4)을 주제별로 동시에 돌리는 풀이다.
     *
     * <h2>⚠ analysisTaskExecutor 를 재사용하면 데드락이다</h2>
     * 오케스트레이터 자신이 그 풀 위에서 돈다. 거기에 주제 작업을 넣고 join 하면, 풀이 찬
     * 순간 **모든 스레드가 자기 하위 작업을 기다리며 서로를 막는다.** 그래서 풀을 나눈다 —
     * 이 풀의 스레드는 아무것도 기다리지 않으므로(계층 호출만 한다) 순환이 생기지 않는다.
     *
     * <h2>왜 공유 풀인가 — 회의마다 만들지 않는다</h2>
     * 동시 실행 상한이 **전체 기준**이어야 한다. 회의마다 풀을 만들면 분석 2건 × 주제 7개가
     * 그대로 14개 동시 호출이 되고, 제공자 쪽 레이트리밋을 우리가 스스로 때린다.
     * 이 풀 하나로 묶으면 분석이 몇 개 돌든 동시 호출은 여기 크기를 넘지 않는다.
     *
     * <h2>포화되면 부른 쪽이 직접 한다(CallerRuns)</h2>
     * 분석 풀과 정책이 다르다. 거기서는 거절이 맞다 — 회의 하나를 나중에 다시 돌리면 된다.
     * 여기서 거절하면 **주제 하나가 통째로 빠진 채 분석이 완료된다.** 그건 요약에서 안건 하나가
     * 조용히 사라지는 것이고 아무도 못 찾는다. CallerRuns 로 두면 최악이 오케스트레이터
     * 스레드가 그 주제를 직접 도는 것 — 즉 **병렬화 이전의 동작으로 떨어질 뿐**이다.
     */
    private static final int TOPIC_POOL_SIZE = 4;

    @Bean("topicTaskExecutor")
    public Executor topicTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(TOPIC_POOL_SIZE);
        executor.setMaxPoolSize(TOPIC_POOL_SIZE);
        /*
         * 큐를 두지 않는다. 큐가 있으면 CallerRuns 가 발동하지 않아 주제가 줄을 서고, 그 줄이
         * 곧 지연이다 — 병렬화의 목적과 반대로 간다. 넘치면 그 자리에서 부른 쪽이 처리한다.
         */
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("analysis-topic-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        /*
         * 종료 시 기다린다 — 주제 호출이 끊기면 그 계층이 실패로 닫히고, 회의는 어차피 다시
         * 돌려야 한다. 분석 풀보다 짧게 잡는다: 이 풀의 작업은 계층 하나의 호출 하나뿐이다.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
