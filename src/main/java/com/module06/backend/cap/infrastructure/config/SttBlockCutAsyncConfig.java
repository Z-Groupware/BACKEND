package com.module06.backend.cap.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/*
 * 10분/40청크 자동 블록 트리거(SttBlockCutTrigger)가 도는 스레드 풀이다
 * (capture의 AnalysisAsyncConfig와 동일 이유 — 전용 풀을 둬야 청크 완료 통보(CAP-07) 응답이
 * ffmpeg·AI-01 호출을 기다리며 늦어지지 않는다. 명세 "이 호출 자체는 즉시 반환한다").
 *
 * @EnableAsync는 여기서 다시 선언하지 않는다 — AnalysisAsyncConfig가 애플리케이션 전역에
 * 이미 켜놨다(@EnableAsync는 컨텍스트당 한 번이면 충분하다).
 */
@Configuration
public class SttBlockCutAsyncConfig {

    private static final int POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 50;

    @Bean("sttBlockCutTaskExecutor")
    public Executor sttBlockCutTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("stt-block-cut-");
        // 포화되면 버린다 — 넘칠 때 청크 완료 통보 요청 스레드가 대신 돌게 하면(CallerRunsPolicy)
        // "즉시 반환한다"는 CAP-07의 계약이 깨진다. 놓친 트리거는 다음 청크가 다시 40개를 채울
        // 때까지 기다리지 않는다 — lastSeq 기준 판정이라 다음 completePartUpload 호출이 다시 감지한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
