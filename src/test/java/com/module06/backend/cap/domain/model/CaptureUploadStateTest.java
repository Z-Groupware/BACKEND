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

        state.recordUpload(7L, 5, 1_000L);
        assertThat(state.getLastSeq()).isEqualTo(5);

        // 더 작은 seq(재전송 등)는 lastSeq를 되돌리지 않는다.
        state.recordUpload(7L, 3, 1_000L);
        assertThat(state.getLastSeq()).isEqualTo(5);
    }

    /*
     * totalBytesUploaded는 lastSeq와 달리 항상 누적된다(metering report용) — 세그먼트 이어받기에도
     * 리셋되지 않는다(이전 세그먼트 청크도 지워지기 전까진 스토리지를 차지하므로).
     */
    @Test
    @DisplayName("recordUpload는 sizeBytes를 항상 누적하고, 세그먼트가 바뀌어도 리셋되지 않는다")
    void recordUploadAccumulatesTotalBytesAcrossSegments() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        state.recordUpload(7L, 1, 1_000L);
        state.recordUpload(7L, 2, 2_000L);
        assertThat(state.getTotalBytesUploaded()).isEqualTo(3_000L);

        state.assignOrVerifyRecorder(9L, true); // 세그먼트 전환 — lastSeq는 리셋되지만
        assertThat(state.getLastSeq()).isZero();
        assertThat(state.getTotalBytesUploaded()).isEqualTo(3_000L); // 누적 바이트는 그대로

        state.recordUpload(9L, 1, 500L);
        assertThat(state.getTotalBytesUploaded()).isEqualTo(3_500L);
    }

    /* 녹음자가 아니면 seq 검증 전에 CAP-004로 막히는지 검증한다. */
    @Test
    @DisplayName("recordUpload는 녹음자가 아니면 CAP-004로 막는다")
    void recordUploadRejectsNonRecorder() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        assertErrorCode(() -> state.recordUpload(9L, 1, 1_000L), "CAP-004");
    }

    /* 범위 밖(0 이하, MAX_SEQ 초과) seq는 CAP-011로 거부해 lastSeq 오염을 막는지 검증한다. */
    @Test
    @DisplayName("recordUpload는 범위 밖 seq를 CAP-011로 거부한다")
    void recordUploadRejectsOutOfRangeSeq() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);

        assertErrorCode(() -> state.recordUpload(7L, 0, 1_000L), "CAP-011");
        assertErrorCode(() -> state.recordUpload(7L, -1, 1_000L), "CAP-011");
        assertErrorCode(() -> state.recordUpload(7L, CaptureUploadState.MAX_SEQ + 1, 1_000L), "CAP-011");
        assertErrorCode(() -> state.recordUpload(7L, Integer.MAX_VALUE, 1_000L), "CAP-011");

        // 거부됐으므로 lastSeq가 오염되지 않아야 한다.
        assertThat(state.getLastSeq()).isZero();

        // 상한 경계값은 허용된다.
        assertThatCode(() -> state.recordUpload(7L, CaptureUploadState.MAX_SEQ, 1_000L)).doesNotThrowAnyException();
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
    @DisplayName("finalizeBlockOffsetIfSegmentMatches는 세그먼트가 같으면 끝 지점만 갱신한다")
    void finalizeBlockOffsetUpdatesOffsetOnly() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.reserveNextBlockSeq();

        boolean applied = state.finalizeBlockOffsetIfSegmentMatches(0, 600_000L);

        assertThat(applied).isTrue();
        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    /* 이전 블록 끝 지점보다 앞서거나 같은 값은 CAP-021로 거절해 블록이 시간을 거스르지 않게 막는지 검증한다. */
    @Test
    @DisplayName("finalizeBlockOffsetIfSegmentMatches는 이전 끝 지점 이하 값을 CAP-021로 거절한다")
    void finalizeBlockOffsetRejectsNonAdvancingOffset() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.finalizeBlockOffsetIfSegmentMatches(0, 600_000L);

        assertErrorCode(() -> state.finalizeBlockOffsetIfSegmentMatches(0, 600_000L), "CAP-021");
        assertErrorCode(() -> state.finalizeBlockOffsetIfSegmentMatches(0, 500_000L), "CAP-021");

        // 거절됐으므로 상태가 그대로여야 한다.
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    /*
     * 세그먼트가 이미 바뀌었으면(예약 당시 세그먼트와 다르면) 적용하지 않고 false를 반환하는지
     * 검증한다(CodeRabbit 지적) — 그 사이 이어받기가 일어나 새 세그먼트가 0으로 리셋된 뒤에
     * 옛 세그먼트의 끝 지점이 도착한 상황을 재현한다.
     */
    @Test
    @DisplayName("finalizeBlockOffsetIfSegmentMatches는 세그먼트가 바뀌었으면 적용하지 않는다")
    void finalizeBlockOffsetSkipsWhenSegmentChanged() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(500L, 7L);
        state.assignOrVerifyRecorder(9L, true); // 세그먼트 0 → 1로 전환, 끝 지점 0으로 리셋됨

        // 옛 세그먼트(0)에서 예약된 파이프라인이 뒤늦게 도착한 상황.
        boolean applied = state.finalizeBlockOffsetIfSegmentMatches(0, 600_000L);

        assertThat(applied).isFalse();
        // 새 세그먼트(1)의 리셋된 값이 오염되지 않아야 한다.
        assertThat(state.getLastBlockEndOffsetMs()).isZero();
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
        state.recordUpload(7L, 25, 1_000L);
        state.finalizeBlockOffsetIfSegmentMatches(0, 150_000L);

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
