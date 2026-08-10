package com.module06.backend.project.infrastructure.storage;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;

/*
 * ProjectAttachmentStoragePort의 실제 S3 구현체 — ProjectAttachmentStorageStubAdapter를 prod에서
 * 대체한다. CapS3ObjectStorageAdapter와 동일 패턴: PUT presigned URL만 서버가 발급하고 실제 바이트는
 * 클라이언트가 S3와 직접 주고받는다(첨부파일이 이 서버를 거치지 않음).
 *
 * S3Client·S3Presigner는 CapS3ClientConfig(@Profile("prod"))가 등록한 애플리케이션 공용 빈을
 * 그대로 주입받는다 — 이 클래스는 별도로 만들지 않는다(리전·자격증명·타임아웃 중복 방지).
 *
 * IssuedUploadUrl.fileUrl은 s3Key 그 자체다(Port 주석 참고) — 조회용 presigned GET은 이번
 * 스코프 밖(업로드·삭제만 요구됨)이라 별도 URL을 만들지 않는다.
 */
@Component
@Profile("prod")
public class ProjectAttachmentS3StorageAdapter implements ProjectAttachmentStoragePort {

    private static final Duration UPLOAD_URL_TTL = Duration.ofSeconds(900); // CAP과 동일 15분

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public ProjectAttachmentS3StorageAdapter(S3Client s3Client, S3Presigner s3Presigner,
            ProjectAttachmentS3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public IssuedUploadUrl issueUploadUrl(String s3Key, long fileSize) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new IssuedUploadUrl(presigned.url().toString(), s3Key);
    }

    @Override
    public void deleteObject(String fileUrl) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(fileUrl)
                .build());
    }
}
