package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.CaptureUploadState;

import java.util.Optional;

// CaptureUploadState 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유
public interface CaptureUploadStateRepository {

    /** 회의당 1행뿐인 상태를 조회 (없으면 아직 presign 호출된 적 없음) */
    Optional<CaptureUploadState> findByMeetingId(Long meetingId);

    /** 상태 저장(신규/갱신 둘 다) */
    CaptureUploadState save(CaptureUploadState state);

    /*
     * 다음 STT 블록 순번을 원자적으로 예약한다(CAS) — SttBlockCutTrigger가 ffmpeg·AI-01 같은
     * 무거운 작업을 시작하기 **전에** 부른다(CodeRabbit 지적: 두 트리거가 동시에 같은 블록을
     * 만들어 Transcribe에 중복 제출하는 경합 방지).
     *
     * SttBlockRepository.markQueuedForRetry(compare-and-set)와 같은 패턴 — DB에 저장된
     * blocksFormed가 expectedBlocksFormed와 **같을 때만** 예약이 성립한다. 다른 트리거가 먼저
     * 가져갔으면(경합에서 졌으면) empty — 호출자는 그냥 이번 회차를 건너뛰어야 한다.
     *
     * @return 예약에 성공했으면 확정된 블록 순번(=예약 전 blocksFormed 값). 실패하면 empty.
     */
    Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed);
}
