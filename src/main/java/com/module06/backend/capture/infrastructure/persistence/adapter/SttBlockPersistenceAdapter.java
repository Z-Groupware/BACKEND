package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttCutReason;
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
     * **조건을 DB 가 판정한다** — 자바에서 비교하지 않는다.
     *
     * 서비스가 이미 같은 트랜잭션에서 이 행을 조회했으므로 엔티티는 영속성 컨텍스트에 올라와
     * 있다. 그 상태에서 id 로 다시 찾아 필드를 비교하면 **자기가 읽은 값과 자기를 비교하는 것**이
     * 된다 — 잠금을 걸어도 캐시된 인스턴스가 돌아오기 때문에 그 사이 바뀐 DB 값이 안 보인다.
     * 그러면 두 요청이 여전히 같은 잡 이름을 만든다(CodeRabbit PR #223 지적).
     *
     * 상태와 시도 횟수를 **조회 조건에** 넣으면 판정이 DB 에서 일어난다. 그 사이 누가 바꿨으면
     * 이 조회가 비어서 돌아오고 진 쪽은 여기서 멈춘다. 쓰기 잠금이 그 구간을 직렬화한다.
     *
     * 상태와 횟수를 **둘 다** 보는 이유 — 상태만 보면 "FAILED → QUEUED → 다시 FAILED" 로 한
     * 바퀴 돈 뒤에도 통과해, 이미 한 번 쓴 잡 이름을 다시 만들게 된다.
     */
    @Override
    @Transactional
    public boolean markQueuedForRetry(long blockId, int expectedRetryCount, String provider,
                                      String providerJobName) {
        Optional<SttBlockJpaEntity> claimed = sttBlockRepository
                .findWithLockByIdAndStatusAndRetryCount(blockId, SttBlockStatus.FAILED, expectedRetryCount);
        if (claimed.isEmpty()) {
            // 다른 요청이 먼저 가져갔거나 그 사이 상태가 바뀌었다. 호출자가 제출을 멈춘다.
            return false;
        }

        SttBlockJpaEntity entity = claimed.get();
        entity.markQueuedForRetry(provider, providerJobName);
        sttBlockRepository.save(entity);
        return true;
    }

    @Override
    @Transactional
    public long createQueued(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs,
                             String cutReason, String audioS3Key, String provider, String providerJobName) {
        SttBlockJpaEntity entity = SttBlockJpaEntity.createQueued(meetingId, blockSeq, startOffsetMs, endOffsetMs,
                SttCutReason.valueOf(cutReason), audioS3Key, provider, providerJobName);
        return sttBlockRepository.save(entity).getId();
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
