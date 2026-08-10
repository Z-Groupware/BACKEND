package com.module06.backend.cap.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/*
 * MeetingCompletedAssemblyTrigger(MEET-08 자동 조립)가 도는 전용 스레드 풀이다.
 *
 * SttBlockCutTrigger와 다르게, 이 트리거는 "다음 청크가 다시 감지"해주는 자연 재시도 경로가
 * 없다 — 회의 종료 이벤트는 한 번만 온다. 그래서 SttBlockCutAsyncConfig의 DiscardPolicy(조용히
 * 버림)는 여기 맞지 않는다 — 놓치면 아무도 모른 채 그 회의는 영원히 조립되지 않는다.
 * capture의 AnalysisAsyncConfig와 같은 이유로 AbortPolicy를 쓴다 — 거절은 로그로 남고,
 * 사람이 CAP-05(수동 녹음 종료)로 다시 트리거할 수 있다.
 *
 * @EnableAsync는 여기서 다시 선언하지 않는다 — AnalysisAsyncConfig가 애플리케이션 전역에
 * 이미 켜놨다(@EnableAsync는 컨텍스트당 한 번이면 충분하다).
 *
 * TODO: 풀 2개·큐 20은 근거 있는 값이 아니라 AnalysisAsyncConfig 값을 그대로 베낀 것이다.
 * analysis는 외부 모델 호출 대기가 대부분(CPU를 거의 안 씀)이라 이 값으로 충분하지만, assembly는
 * ffmpeg가 CPU·디스크를 실제로 점유한다 — 회의 길이별(분)로 처리 시간·CPU·메모리를 실측한 뒤
 * 풀 크기와 RecordingAssemblyS3FfmpegAdapter.FFMPEG_TIMEOUT을 다시 정해야 한다.
 */
@Configuration
public class RecordingAssemblyAsyncConfig {

    private static final int POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 20;

    @Bean("recordingAssemblyTaskExecutor")
    public Executor recordingAssemblyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("recording-assembly-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
