package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;

/*
 * tryReserveNextBlockSeq의 실제 DB CAS 동작을 검증한다 — k6 정합성 테스트(k6/scripts/
 * 01-cap-concurrent-meetings-consistency.js)로 재현된 레이스의 회귀 테스트다.
 *
 * blocksFormed만 CAS로는 부족했다: 첫 트리거가 예약(blocksFormed 0→1)한 뒤 무거운
 * 파이프라인이 도는 동안 lastBlockEndOffsetMs가 아직 안 바뀌는 창에서, 뒤이은(지연된)
 * 트리거가 blocksFormed를 최신값(1)으로 다시 읽어 CAS를 통과시켜 같은 40청크 구간에
 * block_seq=1을 또 예약해버렸다(STT 이중 제출로 이어짐). reservedUpToOffsetMs를 blocksFormed와
 * 같은 트랜잭션 안에서 즉시 전진시켜, "구간이 이미 선점됐는지"를 targetOffsetMs로 다시
 * 검증하게 고쳤다.
 *
 * 이 수정 자체가 또 다른 문제를 만들 뻔했다 — TAIL 마무리는 세그먼트 전환 직후 옛 세그먼트
 * 값으로 예약을 부르는데, reservedUpToOffsetMs는 세그먼트 안에서만 의미가 있어서 그대로
 * 갱신하면 이미 리셋된 새 세그먼트 값을 옛 세그먼트 값으로 오염시킨다. expectedSegmentSeq로
 * 지금 세그먼트와 다르면 오프셋을 건드리지 않도록 방어한다 — 아래 세그먼트 불일치 테스트가 그걸 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("capture_upload_state 블록 예약(CAS) 영속성 어댑터")
class CaptureUploadStatePersistenceAdapterReserveBlockSeqTest {

    private static final Long MEETING_ID = 700L;
    private static final long BLOCK_DURATION_MS = 600_000L; // 15초×40청크
    private static final int SEGMENT_0 = 0;

    @Autowired
    private CaptureUploadStateRepository captureUploadStateRepository;

    @Autowired
    private SpringDataCaptureUploadStateRepository springDataCaptureUploadStateRepository;

    @BeforeEach
    void clear() {
        springDataCaptureUploadStateRepository.deleteAll();
        captureUploadStateRepository.save(CaptureUploadState.startWithRecorder(MEETING_ID, 7L));
    }

    /* 정상 예약 — blocksFormed가 오르고 reservedUpToOffsetMs가 targetOffsetMs로 전진하는지 검증한다. */
    @Test
    @DisplayName("빈 구간에 첫 예약을 하면 성공하고 reservedUpToOffsetMs가 전진한다")
    void firstReservationSucceedsAndAdvancesReservedOffset() {
        Optional<Integer> reserved = captureUploadStateRepository.tryReserveNextBlockSeq(
                MEETING_ID, 0, SEGMENT_0, BLOCK_DURATION_MS, false);

        assertThat(reserved).contains(0);
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(MEETING_ID).orElseThrow();
        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getReservedUpToOffsetMs()).isEqualTo(BLOCK_DURATION_MS);
    }

    /*
     * 회귀 테스트 — 첫 예약이 성공해 blocksFormed가 1로 전진한 뒤, 같은 구간(targetOffsetMs가
     * 이미 선점된 reservedUpToOffsetMs 이하)을 노리는 두 번째 예약 시도는 expectedBlocksFormed를
     * 최신값(1)으로 정확히 맞춰 왔어도(blocksFormed CAS만으로는 안 걸림) 거절돼야 한다 — 이게
     * 바로 k6 테스트로 재현된 버그 상황이다.
     */
    @Test
    @DisplayName("같은 구간을 다시 예약하려 하면 blocksFormed가 맞아도 거절된다(선점 구간 재사용 방지)")
    void secondReservationForSameWindowIsRejectedEvenWithFreshBlocksFormed() {
        Optional<Integer> first = captureUploadStateRepository.tryReserveNextBlockSeq(
                MEETING_ID, 0, SEGMENT_0, BLOCK_DURATION_MS, true);
        assertThat(first).contains(0);

        // 지연된 트리거가 이제서야 실행되며 blocksFormed를 최신값(1)으로 다시 읽어 CAS를 시도한다 —
        // 하지만 targetOffsetMs(같은 600,000ms)는 이미 reservedUpToOffsetMs와 같아 거절돼야 한다.
        Optional<Integer> stale = captureUploadStateRepository.tryReserveNextBlockSeq(
                MEETING_ID, 1, SEGMENT_0, BLOCK_DURATION_MS, false);

        assertThat(stale).isEmpty();
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(MEETING_ID).orElseThrow();
        // block_seq가 하나만 예약된 채로 남아야 한다 — 중복 예약이 없었다는 뜻.
        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getReservedUpToOffsetMs()).isEqualTo(BLOCK_DURATION_MS);
    }

    /* 진짜 새 구간(target이 이미 선점된 값보다 큼)에 대한 다음 예약은 정상적으로 성립하는지 검증한다. */
    @Test
    @DisplayName("다음 구간(target이 더 큼)에 대한 예약은 정상적으로 이어진다")
    void nextGenuineWindowReservationSucceeds() {
        captureUploadStateRepository.tryReserveNextBlockSeq(MEETING_ID, 0, SEGMENT_0, BLOCK_DURATION_MS, true);

        Optional<Integer> second = captureUploadStateRepository.tryReserveNextBlockSeq(
                MEETING_ID, 1, SEGMENT_0, BLOCK_DURATION_MS * 2, false);

        assertThat(second).contains(1);
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(MEETING_ID).orElseThrow();
        assertThat(state.getBlocksFormed()).isEqualTo(2);
        assertThat(state.getReservedUpToOffsetMs()).isEqualTo(BLOCK_DURATION_MS * 2);
    }

    /* blocksFormed 자체가 안 맞으면(기존 CAS) 여전히 거절되는지 검증한다 — 회귀 방지 대상이 아님을 확인. */
    @Test
    @DisplayName("blocksFormed가 기대와 다르면 여전히 거절된다")
    void rejectsWhenBlocksFormedMismatches() {
        Optional<Integer> reserved = captureUploadStateRepository.tryReserveNextBlockSeq(
                MEETING_ID, 5, SEGMENT_0, BLOCK_DURATION_MS, false);

        assertThat(reserved).isEmpty();
    }

    /*
     * 회귀 테스트(2차) — TAIL 마무리가 세그먼트 전환 직후 옛 세그먼트 값으로 예약을 부르는 상황을
     * 재현한다. blocksFormed는 정상적으로 전진해야 하지만(회의 전체를 관통하는 번호라 안전),
     * 이미 새 세그먼트용으로 0 리셋된 reservedUpToOffsetMs는 옛 세그먼트의 targetOffsetMs로
     * 오염되면 안 된다 — 오염되면 다음 정상 트리거의 문턱 판정이 깨진다.
     */
    @Test
    @DisplayName("세그먼트가 이미 바뀐 뒤 예약하면 blocksFormed만 전진하고 reservedUpToOffsetMs는 건드리지 않는다")
    void reservationForStaleSegmentSkipsOffsetUpdate() {
        // 세그먼트 0에서 정상 예약 하나 확정.
        captureUploadStateRepository.tryReserveNextBlockSeq(MEETING_ID, 0, SEGMENT_0, BLOCK_DURATION_MS, true);

        // 이어받기로 세그먼트가 1로 바뀐다 — lastSeq·lastBlockEndOffsetMs·reservedUpToOffsetMs가
        // 전부 0으로 리셋된다(assignOrVerifyRecorder).
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(MEETING_ID).orElseThrow();
        state.assignOrVerifyRecorder(9L, true);
        captureUploadStateRepository.save(state);

        // TAIL 마무리가 옛 세그먼트(0) 값으로 뒤늦게 도착한다 — blocksFormed(1)는 여전히 최신값이지만
        // expectedSegmentSeq(0)는 지금 세그먼트(1)와 다르다.
        Optional<Integer> tailReserved = captureUploadStateRepository.tryReserveNextBlockSeq(
                MEETING_ID, 1, SEGMENT_0, 375_000L, true);

        assertThat(tailReserved).contains(1);
        CaptureUploadState after = captureUploadStateRepository.findByMeetingId(MEETING_ID).orElseThrow();
        assertThat(after.getBlocksFormed()).isEqualTo(2);
        // 옛 세그먼트의 375,000ms로 오염되지 않고 새 세그먼트의 리셋값(0)을 그대로 유지해야 한다.
        assertThat(after.getReservedUpToOffsetMs()).isZero();
        assertThat(after.getSegmentSeq()).isEqualTo(1);
    }
}
