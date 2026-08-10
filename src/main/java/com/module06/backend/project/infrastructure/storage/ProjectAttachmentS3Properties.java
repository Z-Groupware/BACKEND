package com.module06.backend.project.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
 * 프로젝트 첨부파일 오브젝트 스토리지(S3) 버킷 설정 — CapS3Properties와 동일 관용구, 값은
 * CAP과 같은 SSM 파라미터(/z/prod/S3_BUCKET)를 그대로 재사용한다(버킷 하나, prefix로만 구분 —
 * project-attachments/... vs recordings/...). 새 SSM 파라미터를 늘리지 않는다.
 *
 * @Profile("prod")인 ProjectAttachmentS3ClientConfig에서만 바인딩되므로 로컬/테스트는 값이
 * 없어도 부팅에 영향 없다. prod인데 비어 있으면 업로드/삭제가 전부 실패하므로 런타임이 아니라
 * 생성 시점(부팅)에서 끊는다(CapS3Properties와 동일 이유).
 */
@ConfigurationProperties(prefix = "project.attachment.s3")
public record ProjectAttachmentS3Properties(String bucket) {

    public ProjectAttachmentS3Properties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("project.attachment.s3.bucket 이 비어 있습니다. "
                    + "첨부파일 업로드/삭제가 전부 실패하므로 부팅을 중단합니다 (SSM /z/prod/S3_BUCKET).");
        }
    }
}
