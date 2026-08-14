package com.module06.backend.cap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;

/* 비대면 회의 S3 직접 업로드의 파일 형식·5GiB 경계를 검증한다. */
class RecordingFilePolicyTest {

    @Test
    @DisplayName("지원 형식과 정확히 5GiB인 녹음 파일을 허용한다")
    void acceptsSupportedFileAtFiveGibBoundary() {
        String fileName = RecordingFilePolicy.validate(
                "meeting.webm", "audio/webm", RecordingFilePolicy.MAX_SIZE_BYTES);

        assertThat(fileName).isEqualTo("meeting.webm");
    }

    @Test
    @DisplayName("5GiB를 한 바이트 초과한 녹음 파일은 CAP-024로 거절한다")
    void rejectsFileOverFiveGib() {
        assertErrorCode(() -> RecordingFilePolicy.validate(
                "meeting.mp3", "audio/mpeg", RecordingFilePolicy.MAX_SIZE_BYTES + 1), "CAP-024");
    }

    @Test
    @DisplayName("지원하지 않는 확장자는 CAP-025로 거절한다")
    void rejectsUnsupportedExtension() {
        assertErrorCode(() -> RecordingFilePolicy.validate(
                "meeting.exe", "application/octet-stream", 1_024L), "CAP-025");
    }

    @Test
    @DisplayName("확장자와 MIME 타입이 다르면 CAP-025로 거절한다")
    void rejectsMismatchedContentType() {
        assertErrorCode(() -> RecordingFilePolicy.validate(
                "meeting.wav", "audio/mpeg", 1_024L), "CAP-025");
    }

    private void assertErrorCode(Runnable execution, String code) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(code);
    }
}
