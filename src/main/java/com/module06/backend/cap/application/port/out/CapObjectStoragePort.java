package com.module06.backend.cap.application.port.out;

/* comment.
    CAP과 storage 사이의 경계 — 청크 업로드용 presigned PUT URL 발급만 다룬다.
    project의 ProjectAttachmentStoragePort와 동일 패턴. 실제 S3 어댑터가 나오기 전까지 스텁으로 개발한다.
*/
public interface CapObjectStoragePort {

    /** 이 s3Key로 업로드 가능한 presigned PUT URL 하나 발급 */
    IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType);

    /** 이 s3Key의 녹음본을 재생할 presigned GET URL 하나 발급 (HTTP Range 지원 — 탐색바 seek 가능) */
    IssuedPlaybackUrl issuePlaybackUrl(String s3Key);

    record IssuedPartUploadUrl(String presignedUrl, int expiresInSeconds) {
    }

    record IssuedPlaybackUrl(String url, int expiresInSeconds) {
    }
}
