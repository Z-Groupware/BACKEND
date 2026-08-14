package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.IssueOnlineRecordingUploadUrlCommand;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.IssueOnlineRecordingUploadUrlUseCase;

/* 비대면 회의 녹음의 Presigned URL과 테넌트별 pending 키 발급을 검증한다. */
class OnlineRecordingUploadUrlServiceTest {

    @Test
    @DisplayName("파일 바이트를 받지 않고 회사·사용자 범위의 S3 PUT URL을 발급한다")
    void issuesDirectS3UploadUrl() {
        RecordingStorage storage = new RecordingStorage();
        OnlineRecordingUploadUrlService service = new OnlineRecordingUploadUrlService(storage);

        IssueOnlineRecordingUploadUrlUseCase.Result result = service.issueOnlineRecordingUploadUrl(
                new IssueOnlineRecordingUploadUrlCommand(
                        10L, 3L, "meeting.mp3", "audio/mpeg", 1_024L));

        assertThat(result.s3Key())
                .startsWith("recordings/org-10/member-3/online-pending/")
                .endsWith("/meeting.mp3");
        assertThat(result.presignedUrl()).isEqualTo("https://s3.example/upload");
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        assertThat(storage.s3Key).isEqualTo(result.s3Key());
        assertThat(storage.contentType).isEqualTo("audio/mpeg");
    }

    private static final class RecordingStorage implements CapObjectStoragePort {
        private String s3Key;
        private String contentType;

        @Override
        public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
            this.s3Key = s3Key;
            this.contentType = contentType;
            return new IssuedPartUploadUrl("https://s3.example/upload", 900);
        }

        @Override
        public IssuedPlaybackUrl issuePlaybackUrl(String s3Key) {
            throw new AssertionError("업로드 URL 발급에서 재생 URL을 호출하면 안 됩니다.");
        }

        @Override
        public void deleteRecording(String s3Key) {
            throw new AssertionError("정상 URL 발급에서 객체 삭제를 호출하면 안 됩니다.");
        }

        @Override
        public boolean objectMatches(String s3Key, long expectedSizeBytes) {
            throw new AssertionError("URL 발급 단계에서 아직 S3 객체를 조회하면 안 됩니다.");
        }
    }
}
