package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// domain의 CaptureUploadStateRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class CaptureUploadStatePersistenceAdapter implements CaptureUploadStateRepository {

    private final SpringDataCaptureUploadStateRepository springDataCaptureUploadStateRepository;

    public CaptureUploadStatePersistenceAdapter(
            SpringDataCaptureUploadStateRepository springDataCaptureUploadStateRepository) {
        this.springDataCaptureUploadStateRepository = springDataCaptureUploadStateRepository;
    }

    // 회의 id로 현재 세그먼트/녹음자 상태 조회
    @Override
    public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
        return springDataCaptureUploadStateRepository.findById(meetingId).map(CaptureUploadStateJpaEntity::toDomain);
    }

    // 상태 저장(신규 생성 또는 갱신 — meetingId가 PK라 자동으로 upsert)
    @Override
    public CaptureUploadState save(CaptureUploadState state) {
        CaptureUploadStateJpaEntity saved =
                springDataCaptureUploadStateRepository.save(CaptureUploadStateJpaEntity.fromDomain(state));
        return saved.toDomain();
    }

    // @Transactional이 필요하다(CodeRabbit 지적, 실제로 재현·확인됨 — RecordingPartPersistenceAdapter.
    // deleteByMeetingId의 동일한 이유 참고). 파생 삭제 쿼리는 save()와 달리 자체 트랜잭션이 없어,
    // RecordingAssemblyS3FfmpegAdapter처럼 트랜잭션 없는 컨텍스트에서 부르면
    // TransactionRequiredException이 난다.
    @Override
    @Transactional
    public void deleteByMeetingId(Long meetingId) {
        springDataCaptureUploadStateRepository.deleteByMeetingId(meetingId);
    }

    // 조회~잠금~갱신을 한 트랜잭션으로 묶어야 쓰기 잠금이 실제로 경합을 막는다.
    //
    // blocksFormed CAS만으로는 부족하다(k6 정합성 테스트로 재현된 레이스) — 잠금을 잡은 뒤
    // targetOffsetMs를 현재 저장된 reservedUpToOffsetMs와 다시 비교해서, 이미 선점된 구간이면
    // (다른 트리거가 이 트랜잭션 직전에 먼저 예약해갔으면) 거절한다. 무거운 파이프라인이 도는
    // 동안 lastBlockEndOffsetMs가 아직 안 바뀌는 창에서, 뒤이은(지연됐던) 트리거가 같은 구간을
    // 또 문턱 통과로 오판해도 여기서 걸러진다.
    //
    // ⚠️ 이 오프셋 검증·갱신은 지금 세그먼트가 expectedSegmentSeq와 같을 때만 한다
    // (finalizeBlockOffsetIfSegmentMatches와 같은 이유) — TAIL 마무리(SttBlockCutTrigger.
    // finalizeTailBlock)는 세그먼트 전환 직후(assignOrVerifyRecorder가 이미 reservedUpToOffsetMs를
    // 0으로 리셋해둔 뒤) 옛 세그먼트 값으로 이 메서드를 부른다 — 세그먼트가 다르면 오프셋을
    // 건드리지 않고 blocksFormed만 전진시켜야, 이미 리셋된 새 세그먼트의 reservedUpToOffsetMs를
    // 옛 세그먼트 값으로 오염시키지 않는다.
    @Override
    @Transactional
    public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed, int expectedSegmentSeq,
                                                     long targetOffsetMs) {
        Optional<CaptureUploadStateJpaEntity> locked = springDataCaptureUploadStateRepository
                .findWithLockByMeetingIdAndBlocksFormed(meetingId, expectedBlocksFormed);
        if (locked.isEmpty()) {
            // 다른 트리거가 먼저 예약해갔거나, 그 사이 blocksFormed가 바뀌었다 — 경합에서 졌다.
            return Optional.empty();
        }
        CaptureUploadState state = locked.get().toDomain();
        boolean sameSegment = state.getSegmentSeq() == expectedSegmentSeq;
        if (sameSegment && targetOffsetMs <= state.getReservedUpToOffsetMs()) {
            // blocksFormed는 caller가 기대한 값과 같았지만, 이 구간은 이미 선점돼 있다 —
            // 경합에서 졌다(같은 종류의 실패라 로그·반환값을 위 case와 동일하게 다룬다).
            return Optional.empty();
        }
        int reservedSeq = sameSegment
                ? state.reserveNextBlockSeqAndAdvanceOffset(targetOffsetMs)
                : state.reserveNextBlockSeq();
        springDataCaptureUploadStateRepository.save(CaptureUploadStateJpaEntity.fromDomain(state));
        return Optional.of(reservedSeq);
    }
}
