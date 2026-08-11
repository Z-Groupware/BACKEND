package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    // 이 회의의 잔여 청크 조각을 모두 삭제(하드 삭제).
    //
    // @Transactional이 필요하다(CodeRabbit 지적, 실제로 재현·확인됨) — Spring Data는 save()·
    // deleteById() 같은 내장 CRUD 메서드는 SimpleJpaRepository 안에서 자체적으로 @Transactional을
    // 이미 갖고 있어 트랜잭션 없이 불러도 동작하지만, deleteByMeetingId 같은 파생 쿼리 메서드는
    // 그 대상이 아니다. RecordingAssemblyS3FfmpegAdapter.startAssembly()가 이 메서드를
    // 트랜잭션 없는 컨텍스트(@Async 디스패처 뒤, 그 어디에도 @Transactional 없음)에서 부르는데,
    // 여기 트랜잭션이 없으면 jakarta.persistence.TransactionRequiredException이 나고 바깥
    // catch(RuntimeException)에 조용히 삼켜져 "조립 실패" 로그만 남긴다 — 이 세션 코드가 아니라
    // 원래 있던 잠재 버그였다(직접 재현 테스트로 확인).
    @Override
    @Transactional
    public void deleteByMeetingId(Long meetingId) {
        springDataRecordingPartRepository.deleteByMeetingId(meetingId);
    }

    // [fromSeq, toSeq] 범위의 청크 행 전체(seq 순서) — 블록 오디오 조립(ffmpeg)이 실제 s3Key를 읽는다.
    @Override
    public List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq, int toSeq) {
        return springDataRecordingPartRepository
                .findByMeetingIdAndSegmentSeqAndSeqBetweenOrderBySeqAsc(meetingId, segmentSeq, fromSeq, toSeq)
                .stream()
                .map(RecordingPartJpaEntity::toDomain)
                .toList();
    }
}
