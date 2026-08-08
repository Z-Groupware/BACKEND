package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.domain.model.SttBlockStatus;
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

    /*
     * **쓰기 잠금을 걸고 다시 확인한 뒤** 바꾼다.
     *
     * 서비스가 읽은 스냅샷과 지금 행이 같아야 전이한다 — 그 사이에 다른 재처리 요청이 끼어들어
     * 시도 횟수를 올렸다면 서비스가 만든 잡 이름은 이미 남의 것과 겹친다. 그대로 제출하면
     * 계정 내 중복 이름으로 거절되는데, 그게 이름에 횟수를 넣어 막으려던 상황이다
     * (CodeRabbit PR #223 지적).
     */
    @Override
    @Transactional
    public boolean markQueuedForRetry(long blockId, int expectedRetryCount, String provider,
                                      String providerJobName) {
        SttBlockJpaEntity entity = sttBlockRepository.findWithLockById(blockId)
                // 방금 읽은 행이 사라졌다 = 우리 버그이거나 동시 삭제다. 조용히 넘기면
                // 응답의 retryCount 가 거짓이 되고, 사람은 접수됐다고 믿는다.
                .orElseThrow(() -> new IllegalStateException(
                        "재처리할 STT 블록을 찾을 수 없습니다. blockId=" + blockId));

        /*
         * 상태와 횟수를 **둘 다** 본다. 상태만 보면 "FAILED → QUEUED → 다시 FAILED" 로 한 바퀴
         * 돈 뒤에도 통과해, 이미 한 번 쓴 잡 이름을 다시 만들게 된다.
         */
        if (entity.getStatus() != SttBlockStatus.FAILED || entity.getRetryCount() != expectedRetryCount) {
            return false;
        }

        entity.markQueuedForRetry(provider, providerJobName);
        sttBlockRepository.save(entity);
        return true;
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
