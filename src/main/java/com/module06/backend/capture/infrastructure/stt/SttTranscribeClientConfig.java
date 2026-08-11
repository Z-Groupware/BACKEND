package com.module06.backend.capture.infrastructure.stt;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

/*
 * Transcribe 클라이언트 빈. @Profile("prod") 만 — 로컬·테스트는 SttJobStubAdapter 가 대신하므로
 * AWS 자격증명이 전혀 필요 없다(CapS3ClientConfig 와 같은 구조).
 *
 * 자격증명은 DefaultCredentialsProvider 로 받는다. **Transcribe IAM 롤은 Spring EC2 에 있다**
 * (역할 분담 — AI 서버로 넘기지 않는다). 정적 키를 SSM 에 두지 않으므로 유출 표면이 없고
 * 로테이션도 AWS 가 한다.
 *
 * <h2>시간 제한을 명시한다</h2>
 * 제출은 사람이 재처리 버튼을 눌러 기다리는 동기 경로이고(STT-04 → 202), **트랜잭션 안에서**
 * 불린다(SttBlockService.retry · SttBlockCreationService). 제한이 없으면 Transcribe 지연에
 * SDK 기본 재시도가 겹쳐 DB 커넥션을 오래 붙들고, 풀 고갈로 번진다 — CapS3ClientConfig 가
 * 같은 이유로 같은 값을 쓴다.
 *
 * ⚠️ 이 값이 짧다고 제출이 유실되는 것은 아니다. 타임아웃은 **응답을 못 받은 것**이고 잡은
 * 이미 접수됐을 수 있다 — 그 경우 재처리가 같은 잡 이름으로 다시 제출해 제공자가 거절한다
 * (계정 내 유일 제약). 그래서 잡 이름에 retryCount 가 들어 있다.
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(SttTranscribeProperties.class)
public class SttTranscribeClientConfig {

    private static final Region REGION = Region.AP_NORTHEAST_2;
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public TranscribeClient sttTranscribeClient() {
        return TranscribeClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                        .build())
                .build();
    }
}
