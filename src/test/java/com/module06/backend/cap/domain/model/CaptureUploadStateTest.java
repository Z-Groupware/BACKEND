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

    /* 블록 순번 예약 시 blocksFormed가 오르고, 예약 전 값을 반환하는지 검증한다. */
    @Test
    @DisplayName("reserveNextBlockSeq는 예약 전 순번을 반환하고 blocksFormed를 올린다")
    void reserveNextBlockSeqReturnsPreviousValueAndAdvancesCount() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        int firstReserved = state.reserveNextBlockSeq();
        int secondReserved = state.reserveNextBlockSeq();

        assertThat(firstReserved).isZero();
        assertThat(secondReserved).isEqualTo(1);
        assertThat(state.getBlocksFormed()).isEqualTo(2);
    }

    /* 예약된 블록이 완성되면 끝 지점만 갱신되고(blocksFormed는 안 건드림) 검증한다. */
    @Test
    @DisplayName("finalizeBlockOffset은 끝 지점만 갱신한다")
    void finalizeBlockOffsetUpdatesOffsetOnly() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.reserveNextBlockSeq();

        state.finalizeBlockOffset(600_000L);

        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    /* 이전 블록 끝 지점보다 앞서거나 같은 값은 CAP-021로 거절해 블록이 시간을 거스르지 않게 막는지 검증한다. */
    @Test
    @DisplayName("finalizeBlockOffset은 이전 끝 지점 이하 값을 CAP-021로 거절한다")
    void finalizeBlockOffsetRejectsNonAdvancingOffset() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.finalizeBlockOffset(600_000L);

        assertErrorCode(() -> state.finalizeBlockOffset(600_000L), "CAP-021");
        assertErrorCode(() -> state.finalizeBlockOffset(500_000L), "CAP-021");

        // 거절됐으므로 상태가 그대로여야 한다.
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    /* willChangeSegment가 실제 이어받기 성립 조건(assignOrVerifyRecorder와 동일)만 true를 내는지 검증한다. */
    @Test
    @DisplayName("willChangeSegment는 다른 사람이 canTakeover=true로 부를 때만 true다")
    void willChangeSegmentMatchesTakeoverCondition() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        assertThat(state.willChangeSegment(7L, true)).isFalse();   // 본인 재호출
        assertThat(state.willChangeSegment(9L, false)).isFalse();  // 하트비트 살아있음
        assertThat(state.willChangeSegment(9L, true)).isTrue();    // 이어받기 성립
    }

    /*
     * 세그먼트 전환(이어받기 성립)이 lastSeq·lastBlockEndOffsetMs를 0으로 리셋하는지 검증한다
     * (CodeRabbit 지적 — 예전엔 이 리셋이 없어서 RecordingAssemblyService.hasSeqGap의 "매
     * 세그먼트는 seq=1부터" 가정과 실제 상태가 어긋나 있었다).
     */
    @Test
    @DisplayName("이어받기로 세그먼트가 바뀌면 lastSeq와 블록 끝 지점이 0으로 리셋된다")
    void takeoverResetsSeqAndBlockOffsetForNewSegment() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.recordUpload(7L, 25);
        state.finalizeBlockOffset(150_000L);

        state.assignOrVerifyRecorder(9L, true);

        assertThat(state.getSegmentSeq()).isEqualTo(1);
        assertThat(state.getLastSeq()).isZero();
        assertThat(state.getLastBlockEndOffsetMs()).isZero();
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
