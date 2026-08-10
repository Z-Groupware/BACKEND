package com.module06.backend.project.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/*
 * ProjectAttachmentS3Properties 바인딩만 켠다 — S3Client·S3Presigner 빈은 새로 안 만든다.
 * CapS3ClientConfig(@Profile("prod"))가 이미 애플리케이션 전체에서 하나씩만 등록해 두므로,
 * ProjectAttachmentS3StorageAdapter는 그 빈을 타입으로 그대로 주입받는다(같은 리전·자격증명·
 * 타임아웃 설정 공유 — 버킷 하나를 두 도메인이 나눠 쓰는 구조와 일관됨).
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(ProjectAttachmentS3Properties.class)
public class ProjectAttachmentS3Config {
}
