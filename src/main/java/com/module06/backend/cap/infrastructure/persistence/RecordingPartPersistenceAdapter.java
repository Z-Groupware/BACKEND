package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;

// domain의 RecordingPartRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class RecordingPartPersistenceAdapter implements RecordingPartRepository {

    private final SpringDataRecordingPartRepository springDataRecordingPartRepository;

    public RecordingPartPersistenceAdapter(SpringDataRecordingPartRepository springDataRecordingPartRepository) {
        this.springDataRecordingPartRepository = springDataRecordingPartRepository;
    }

    // 청크 하나를 저장 (멱등 위반 시 409로 변환)
    @Override
    public RecordingPart save(RecordingPart recordingPart) {
        try {
            // saveAndFlush로 UNIQUE(meeting_id, segment_seq, seq) 위반을 이 try 안에서 즉시 유발한다.
            // save()만 쓰면 flush가 서비스 트랜잭션 커밋(어댑터 바깥) 시점으로 밀려 아래 catch를 못 잡고 500으로 샌다.
            RecordingPartJpaEntity saved =
                    springDataRecordingPartRepository.saveAndFlush(RecordingPartJpaEntity.fromDomain(recordingPart));
            return saved.toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CapErrorCode.CAP_PART_ALREADY_REGISTERED);
        }
    }

    // 현재 세그먼트에 행이 존재하는 청크 순번 목록 (seq만 뽑는 프로젝션 → 상태 무관, missingSeqs 계산용)
    @Override
    public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
        return springDataRecordingPartRepository.findByMeetingIdAndSegmentSeq(meetingId, segmentSeq)
                .stream()
                .map(SpringDataRecordingPartRepository.SeqView::getSeq)
                .toList();
    }
}
