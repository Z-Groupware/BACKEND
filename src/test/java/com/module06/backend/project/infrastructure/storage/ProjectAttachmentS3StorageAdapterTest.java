package com.module06.backend.project.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;

/*
 * S3 프로덕션 어댑터의 presigned PUT URL 서명 파라미터·만료·삭제 요청 조립을 검증한다.
 * CapS3ObjectStorageAdapterTest와 동일 근거 — presign*은 순수 로컬 SigV4 계산이라 네트워크 호출이
 * 없다(가짜 정적 자격증명으로 충분). delete만 실제 API 호출이라 Mockito로 검증한다.
 */
@DisplayName("프로젝트 첨부파일 S3 스토리지 어댑터")
class ProjectAttachmentS3StorageAdapterTest {

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "project-attachments/company-1/project-100/uuid-spec.pdf";

    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();

    @Test
    @DisplayName("presign PUT: 버킷·키 서명이 담긴 URL과 900초 만료를 반환하고, fileUrl은 s3Key 그대로다")
    void issueUploadUrl_returnsSignedPutUrlAndKeyAsFileUrl() {
        ProjectAttachmentS3StorageAdapter adapter = adapter();

        IssuedUploadUrl issued = adapter.issueUploadUrl(KEY, 1024L);

        assertThat(issued.uploadUrl()).contains(BUCKET).contains(KEY).contains("X-Amz-Expires=900");
        assertThat(issued.fileUrl()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("presign GET: 버킷·키 서명이 담긴 URL과 300초 만료를 반환한다")
    void issueDownloadUrl_returnsSignedGetUrl() {
        ProjectAttachmentS3StorageAdapter adapter = adapter();

        IssuedDownloadUrl issued = adapter.issueDownloadUrl(KEY);

        assertThat(issued.downloadUrl()).contains(BUCKET).contains(KEY).contains("X-Amz-Expires=300");
        assertThat(issued.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("delete: 지정한 버킷·키로 DeleteObject를 호출한다")
    void deleteObject_callsDeleteObjectWithBucketAndKey() {
        S3Client s3Client = mock(S3Client.class);
        ProjectAttachmentS3StorageAdapter adapter =
                new ProjectAttachmentS3StorageAdapter(s3Client, presigner, properties());

        adapter.deleteObject(KEY);

        org.mockito.ArgumentCaptor<DeleteObjectRequest> captor =
                org.mockito.ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(KEY);
    }

    private ProjectAttachmentS3StorageAdapter adapter() {
        return new ProjectAttachmentS3StorageAdapter(mock(S3Client.class), presigner, properties());
    }

    private ProjectAttachmentS3Properties properties() {
        return new ProjectAttachmentS3Properties(BUCKET);
    }
}
