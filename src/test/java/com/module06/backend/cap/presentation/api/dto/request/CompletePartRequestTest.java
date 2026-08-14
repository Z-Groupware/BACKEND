package com.module06.backend.cap.presentation.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.domain.model.RecordingPart;

/*
 * CompletePartRequest.sizeBytes의 하한/상한 검증 — ManualRecordingRequest는 이미 @Positive였는데
 * 여기(청크 완료 통보, CAP-07)만 @NotNull뿐이던 비대칭을 없앤다. RecordingPart 생성자가 결국 같은
 * 검증을 하긴 하지만, 그건 objectMatches S3 HEAD 호출 이후라 여기서 먼저 걸러야 요청 경계에서
 * 바로 400으로 끝난다.
 */
@DisplayName("CompletePartRequest 검증")
class CompletePartRequestTest {

    private static final String KEY = "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("sizeBytes가 0 이하면 제약 위반이다")
    void rejectsNonPositiveSize() {
        CompletePartRequest request = new CompletePartRequest(0, KEY, 0L);

        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<CompletePartRequest>) violation)
                        .getPropertyPath().toString())
                .contains("sizeBytes");
    }

    @Test
    @DisplayName("sizeBytes가 청크 상한(RecordingPart.MAX_SIZE_BYTES)을 넘으면 제약 위반이다")
    void rejectsSizeOverMax() {
        CompletePartRequest request = new CompletePartRequest(0, KEY, RecordingPart.MAX_SIZE_BYTES + 1);

        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<CompletePartRequest>) violation)
                        .getPropertyPath().toString())
                .contains("sizeBytes");
    }

    @Test
    @DisplayName("sizeBytes가 상한 이내면 제약 위반이 없다")
    void acceptsSizeWithinRange() {
        CompletePartRequest request = new CompletePartRequest(0, KEY, RecordingPart.MAX_SIZE_BYTES);

        assertThat(validator.validate(request)).isEmpty();
    }
}
