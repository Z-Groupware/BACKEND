package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import org.springframework.stereotype.Repository;

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
}
