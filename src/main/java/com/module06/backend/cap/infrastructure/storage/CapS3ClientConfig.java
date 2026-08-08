package com.module06.backend.cap.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/*
 * CAP S3 클라이언트/프리사이너 빈. @Profile("prod")만 — 로컬/테스트는 CapObjectStorageStubAdapter가
 * 대신하므로 AWS 자격증명이 전혀 필요 없다.
 *
 * 자격증명은 DefaultCredentialsProvider(체인)로 받는다 — 운영 EC2 인스턴스에 붙은 IAM 역할을
 * 자동으로 줍는다. 액세스키/시크릿키를 SSM에 따로 안 둔다: 정적 키보다 인스턴스 역할이
 * 안전하고(키 유출 표면 자체가 없음), 로테이션도 AWS가 알아서 한다.
 *
 * 리전은 하드코딩(ap-northeast-2) — CapS3Properties 주석 참고.
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(CapS3Properties.class)
public class CapS3ClientConfig {

    private static final Region REGION = Region.AP_NORTHEAST_2;

    @Bean
    public S3Client capS3Client() {
        return S3Client.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner capS3Presigner() {
        return S3Presigner.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
