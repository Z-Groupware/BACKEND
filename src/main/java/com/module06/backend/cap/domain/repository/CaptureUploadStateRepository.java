package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.CaptureUploadState;

import java.util.Optional;

// CaptureUploadState 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유
public interface CaptureUploadStateRepository {

    /** 회의당 1행뿐인 상태를 조회 (없으면 아직 presign 호출된 적 없음) */
    Optional<CaptureUploadState> findByMeetingId(Long meetingId);

    /** 상태 저장(신규/갱신 둘 다) */
    CaptureUploadState save(CaptureUploadState state);

    /**
     * 이 회의의 상태 행을 지운다 — 조립 완료(RecordingAssemblyS3FfmpegAdapter)·녹음 삭제
     * (DeleteRecordingService) 시점에 호출한다. 안 지우면 이미 끝난 회의의 meetingId로 presign을
     * 다시 부를 때 findByMeetingId가 여전히 값을 돌려줘 "새 회의 시작" 판정(저장 용량 한도 확인)을
     * 건너뛴다(CodeRabbit 지적).
     */
    void deleteByMeetingId(Long meetingId);

    /*
     * 다음 STT 블록 순번을 원자적으로 예약한다(CAS) — SttBlockCutTrigger가 ffmpeg·AI-01 같은
     * 무거운 작업을 시작하기 **전에** 부른다(CodeRabbit 지적: 두 트리거가 동시에 같은 블록을
     * 만들어 Transcribe에 중복 제출하는 경합 방지).
     *
     * SttBlockRepository.markQueuedForRetry(compare-and-set)와 같은 패턴 — DB에 저장된
     * blocksFormed가 expectedBlocksFormed와 **같을 때만** 예약이 성립한다. 다른 트리거가 먼저
     * 가져갔으면(경합에서 졌으면) empty — 호출자는 그냥 이번 회차를 건너뛰어야 한다.
     *
     * targetOffsetMs도 같이 넘긴다 — blocksFormed CAS만으로는 "이 구간을 이미 선점했는지"를
     * 못 막는다(k6 정합성 테스트로 재현된 레이스: 무거운 파이프라인이 도는 동안 lastBlockEndOffsetMs가
     * 아직 안 바뀌어서, 뒤이은 트리거가 같은 구간을 또 문턱 통과로 오판해 blocksFormed CAS까지
     * 통과해버렸다 — block_seq 중복 생성 + STT 이중 제출). 그래서 이 메서드는 blocksFormed CAS와
     * 같은 트랜잭션 안에서, **지금 세그먼트가 expectedSegmentSeq와 같을 때만** targetOffsetMs가
     * 현재 저장된 reservedUpToOffsetMs보다 큰지 확인하고, 성립하면 reservedUpToOffsetMs를
     * targetOffsetMs로 즉시 전진시킨다.
     *
     * expectedSegmentSeq가 지금 세그먼트와 다르면(그 사이 이어받기가 일어났으면) 오프셋 검증·갱신을
     * 건너뛰고 blocksFormed만 전진시킨다 — reservedUpToOffsetMs는 lastBlockEndOffsetMs와 마찬가지로
     * 그 세그먼트 안에서만 의미가 있어서, 옛 세그먼트의 값을 새 세그먼트(이미 0으로 리셋됨)에
     * 적용하면 오염된다(finalizeBlockOffsetIfSegmentMatches와 같은 이유 — TAIL 마무리가 세그먼트
     * 전환 직후 옛 세그먼트 값으로 이 메서드를 부르는 경로가 실제로 있다).
     *
     * <h2>진행 중인 예약이 있으면 거절한다(CodeRabbit 지적, 2차)</h2>
     * targetOffsetMs 검증만으로는 "같은 구간 재예약"만 막힌다 — 앞 블록이 아직 안 끝난 채(무거운
     * 파이프라인이 도는 중) 청크가 계속 쌓여 다음 블록이 먼저 예약되면, 두 블록 다 여전히 옛
     * lastBlockEndOffsetMs를 오디오 시작점으로 써서 서로 겹치는 오디오를 만든다. 그래서
     * CaptureUploadState.hasNoPendingReservation()(blocksFormed == finalizedBlocksCount)이
     * true일 때만 새 예약을 허용한다 — 세그먼트당 진행 중인 예약을 1개로 제한한다.
     *
     * @param completesSynchronously 이 예약이 즉시(동기로) 끝나는가 — TAIL(finalizeTailBlock)처럼
     *                               조립·제출까지 한 번에 끝나 별도 완료 통보가 없는 경우 true다.
     *                               true면 예약 성공과 동시에 CaptureUploadState.markBlockFinalized()로
     *                               "끝남"까지 같이 표시한다. false(일반 트리거)면 표시하지 않고,
     *                               나중에 SttBlockFormedWriter.finalizeBlockOffset이 대신 표시한다 —
     *                               그때까지는 hasNoPendingReservation()이 false라 다음 예약이 막힌다.
     * @return 예약에 성공했으면 확정된 블록 순번(=예약 전 blocksFormed 값). 실패하면 empty.
     */
    Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed, int expectedSegmentSeq,
                                             long targetOffsetMs, boolean completesSynchronously);
}
