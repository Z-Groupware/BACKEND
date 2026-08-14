package com.module06.backend.cap.presentation.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * PresignPartsRequest.count의 배치 상한(MAX_COUNT) 검증 — 상한이 없으면 요청 하나로 과도한 수의
 * presigned URL 서명 연산을 시켜 응답을 부풀릴 수 있다(DoS 방어, RecordingAssemblyService의
 * isOutOfRange와 동일 취지).
 */
@DisplayName("PresignPartsRequest 검증")
class PresignPartsRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("count가 상한(100)을 넘으면 제약 위반이다")
    void rejectsCountOverMax() {
        PresignPartsRequest request = new PresignPartsRequest(101, "audio/webm");

        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<PresignPartsRequest>) violation)
                        .getPropertyPath().toString())
                .contains("count");
    }

    @Test
    @DisplayName("count가 0 이하면 제약 위반이다")
    void rejectsNonPositiveCount() {
        PresignPartsRequest request = new PresignPartsRequest(0, "audio/webm");

        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<PresignPartsRequest>) violation)
                        .getPropertyPath().toString())
                .contains("count");
    }

    @Test
    @DisplayName("count가 상한 이내면 제약 위반이 없다")
    void acceptsCountWithinRange() {
        PresignPartsRequest request = new PresignPartsRequest(100, "audio/webm");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("count가 null이면(기본값으로 대체될 값) 제약 위반이 없다")
    void acceptsNullCount() {
        PresignPartsRequest request = new PresignPartsRequest(null, "audio/webm");

        assertThat(validator.validate(request)).isEmpty();
    }
}
