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
    @Override
    @Transactional
    public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed) {
        Optional<CaptureUploadStateJpaEntity> locked = springDataCaptureUploadStateRepository
                .findWithLockByMeetingIdAndBlocksFormed(meetingId, expectedBlocksFormed);
        if (locked.isEmpty()) {
            // 다른 트리거가 먼저 예약해갔거나, 그 사이 blocksFormed가 바뀌었다 — 경합에서 졌다.
            return Optional.empty();
        }
        CaptureUploadState state = locked.get().toDomain();
        int reservedSeq = state.reserveNextBlockSeq();
        springDataCaptureUploadStateRepository.save(CaptureUploadStateJpaEntity.fromDomain(state));
        return Optional.of(reservedSeq);
    }
}
