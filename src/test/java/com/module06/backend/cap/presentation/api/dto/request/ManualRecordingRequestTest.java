package com.module06.backend.cap.presentation.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * ManualRecordingRequest.sizeBytes의 상한(MAX_SIZE_BYTES=5GiB) 검증 — CapS3ObjectStorageAdapter가
 * 발급하는 게 단일 presigned PUT(멀티파트 아님)이라 S3 자체가 5GiB를 넘는 업로드를 거부한다.
 * 상한이 없으면 클라이언트가 주장한 sizeBytes를 검증 없이 그대로 메타로 등록해버릴 수 있었다
 * (청크 경로의 RecordingPart.MAX_SIZE_BYTES와 달리 CAP-10엔 이 상한이 어디에도 없었다).
 */
@DisplayName("ManualRecordingRequest 검증")
class ManualRecordingRequestTest {

    private static final String KEY = "recordings/org-1/meeting-500/3f8e6b0a-1a2b-4c3d-9e0f-abcdef123456.ogg";
    private static final String FILE_NAME = "recording.ogg";
    private static final long FIVE_GIB = 5L * 1024 * 1024 * 1024;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("sizeBytes가 상한(5GiB)을 넘으면 제약 위반이다")
    void rejectsSizeOverMax() {
        ManualRecordingRequest request = new ManualRecordingRequest(KEY, FILE_NAME, FIVE_GIB + 1);

        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<ManualRecordingRequest>) violation)
                        .getPropertyPath().toString())
                .contains("sizeBytes");
    }

    @Test
    @DisplayName("sizeBytes가 상한 이내면 제약 위반이 없다")
    void acceptsSizeWithinRange() {
        ManualRecordingRequest request = new ManualRecordingRequest(KEY, FILE_NAME, FIVE_GIB);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("sizeBytes가 0 이하면 제약 위반이다")
    void rejectsNonPositiveSize() {
        ManualRecordingRequest request = new ManualRecordingRequest(KEY, FILE_NAME, 0L);

        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<ManualRecordingRequest>) violation)
                        .getPropertyPath().toString())
                .contains("sizeBytes");
    }
}
