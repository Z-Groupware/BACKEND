package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// domain의 RecordingRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class RecordingPersistenceAdapter implements RecordingRepository {

    private final SpringDataCapRecordingRepository springDataCapRecordingRepository;

    public RecordingPersistenceAdapter(SpringDataCapRecordingRepository springDataCapRecordingRepository) {
        this.springDataCapRecordingRepository = springDataCapRecordingRepository;
    }

    @Override
    public Recording save(Recording recording) {
        try {
            // saveAndFlush로 UNIQUE(meeting_id) 위반을 이 try 안에서 즉시 유발한다(service의 선검사는 TOCTOU라
            // 동시 요청 시 통과할 수 있어, DB 제약이 최종 방어선). recording_part 저장과 동일 패턴.
            return springDataCapRecordingRepository.saveAndFlush(CapRecordingJpaEntity.fromDomain(recording)).toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_ALREADY_SUBMITTED);
        }
    }

    @Override
    public boolean existsByMeetingId(Long meetingId) {
        return springDataCapRecordingRepository.existsByMeetingId(meetingId);
    }

    @Override
    public Optional<Recording> findByMeetingId(Long meetingId) {
        return springDataCapRecordingRepository.findByMeetingId(meetingId).map(CapRecordingJpaEntity::toDomain);
    }
}
