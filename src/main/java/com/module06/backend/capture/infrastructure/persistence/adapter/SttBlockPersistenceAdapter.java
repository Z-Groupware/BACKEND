package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.infrastructure.persistence.entity.SttBlockJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataSttBlockRepository;

/*
 * stt_block 접근 어댑터다(STT-03 · STT-04).
 *
 * 상태 기록에 REQUIRES_NEW 를 쓰지 않는다. 분석 계층(analysis_layer)과 다른 성질이다 —
 * 그쪽은 "실패해도 흔적이 남아야" 하므로 바깥 트랜잭션과 생사를 갈랐지만, 여기 재처리는
 * **제출과 상태가 함께 참이어야 한다.** 제출이 실패했는데 QUEUED 만 남으면 그 블록은 다시
 * 누를 수도 없는 상태가 된다(QUEUED 는 재처리 대상이 아니다).
 */
@Repository
@RequiredArgsConstructor
public class SttBlockPersistenceAdapter implements SttBlockRepository {

    private final SpringDataSttBlockRepository sttBlockRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SttBlockView> findByMeeting(long meetingId) {
        return sttBlockRepository.findByMeetingIdOrderByBlockSeqAsc(meetingId).stream()
                .map(SttBlockPersistenceAdapter::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SttBlockView> findOne(long meetingId, int blockSeq) {
        return sttBlockRepository.findByMeetingIdAndBlockSeq(meetingId, blockSeq)
                .map(SttBlockPersistenceAdapter::toView);
    }

    @Override
    @Transactional
    public int markQueuedForRetry(long blockId, String provider, String providerJobName) {
        SttBlockJpaEntity entity = sttBlockRepository.findById(blockId)
                // 방금 읽은 행이 사라졌다 = 우리 버그이거나 동시 삭제다. 조용히 0 을 돌려주면
                // 응답의 retryCount 가 거짓이 되고, 사람은 접수됐다고 믿는다.
                .orElseThrow(() -> new IllegalStateException(
                        "재처리할 STT 블록을 찾을 수 없습니다. blockId=" + blockId));

        int retryCount = entity.markQueuedForRetry(provider, providerJobName);
        sttBlockRepository.save(entity);
        return retryCount;
    }

    private static SttBlockView toView(SttBlockJpaEntity entity) {
        return new SttBlockView(
                entity.getId(),
                entity.getBlockSeq(),
                entity.getStartOffsetMs(),
                entity.getEndOffsetMs(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getCutReason(),
                entity.getRetryCount(),
                entity.getErrorCode(),
                entity.getAudioS3Key());
    }
}
