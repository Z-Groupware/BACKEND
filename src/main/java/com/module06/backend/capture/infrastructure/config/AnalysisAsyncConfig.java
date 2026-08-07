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
         * 남은 RUNNING 을 되찾는 경로(오래된 RUNNING 을 FAILED 로 내리는 회수 작업)는 아직 없다.
         * 이 설정이 그 필요를 줄일 뿐이고, 없애지는 못한다.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180);
        executor.initialize();
        return executor;
    }
}
