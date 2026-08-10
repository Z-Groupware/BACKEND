package com.module06.backend.cap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;

/*
 * CaptureUploadState 애그리거트의 녹음자 검증과 청크 순번 범위 강제(DoS 방어) 불변식을 검증하는 단위 테스트다.
 */
@DisplayName("CaptureUploadState 도메인 규칙")
class CaptureUploadStateTest {

    /* 현재 녹음자면 검증을 통과하고, 아니면 CAP-004로 막히는지 검증한다. */
    @Test
    @DisplayName("verifyRecorder는 현재 녹음자만 통과시킨다")
    void verifyRecorderOnlyAllowsRecorder() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        assertThatCode(() -> state.verifyRecorder(7L)).doesNotThrowAnyException();
        assertErrorCode(() -> state.verifyRecorder(9L), "CAP-004");
    }

    /* 정상 범위 seq는 lastSeq를 단조 증가시키는지 검증한다. */
    @Test
    @DisplayName("recordUpload는 정상 seq로 lastSeq를 올린다")
    void recordUploadAdvancesLastSeq() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        state.recordUpload(7L, 5);
        assertThat(state.getLastSeq()).isEqualTo(5);

        // 더 작은 seq(재전송 등)는 lastSeq를 되돌리지 않는다.
        state.recordUpload(7L, 3);
        assertThat(state.getLastSeq()).isEqualTo(5);
    }

    /* 녹음자가 아니면 seq 검증 전에 CAP-004로 막히는지 검증한다. */
    @Test
    @DisplayName("recordUpload는 녹음자가 아니면 CAP-004로 막는다")
    void recordUploadRejectsNonRecorder() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        assertErrorCode(() -> state.recordUpload(9L, 1), "CAP-004");
    }

    /* 범위 밖(0 이하, MAX_SEQ 초과) seq는 CAP-011로 거부해 lastSeq 오염을 막는지 검증한다. */
    @Test
    @DisplayName("recordUpload는 범위 밖 seq를 CAP-011로 거부한다")
    void recordUploadRejectsOutOfRangeSeq() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        assertErrorCode(() -> state.recordUpload(7L, 0), "CAP-011");
        assertErrorCode(() -> state.recordUpload(7L, -1), "CAP-011");
        assertErrorCode(() -> state.recordUpload(7L, CaptureUploadState.MAX_SEQ + 1), "CAP-011");
        assertErrorCode(() -> state.recordUpload(7L, Integer.MAX_VALUE), "CAP-011");

        // 거부됐으므로 lastSeq가 오염되지 않아야 한다.
        assertThat(state.getLastSeq()).isZero();

        // 상한 경계값은 허용된다.
        assertThatCode(() -> state.recordUpload(7L, CaptureUploadState.MAX_SEQ)).doesNotThrowAnyException();
    }

    /* 블록 형성 시 개수가 오르고 끝 지점이 갱신되는지 검증한다. */
    @Test
    @DisplayName("recordBlockFormed는 blocksFormed를 올리고 끝 지점을 갱신한다")
    void recordBlockFormedAdvancesCountAndOffset() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        state.recordBlockFormed(600_000L);

        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    /* 이전 블록 끝 지점보다 앞서거나 같은 값은 CAP-021로 거절해 블록이 시간을 거스르지 않게 막는지 검증한다. */
    @Test
    @DisplayName("recordBlockFormed는 이전 끝 지점 이하 값을 CAP-021로 거절한다")
    void recordBlockFormedRejectsNonAdvancingOffset() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.recordBlockFormed(600_000L);

        assertErrorCode(() -> state.recordBlockFormed(600_000L), "CAP-021");
        assertErrorCode(() -> state.recordBlockFormed(500_000L), "CAP-021");

        // 거절됐으므로 상태가 그대로여야 한다.
        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
