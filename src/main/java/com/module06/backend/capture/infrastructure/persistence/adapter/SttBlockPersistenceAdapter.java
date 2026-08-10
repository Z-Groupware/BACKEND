package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
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

    /*
     * "아직 결과가 안 나온" 상태들 — 이 목록이 어댑터에 있는 이유는 SttBlockStatus 의 의미를
     * 아는 쪽이 여기이기 때문이다. 상태를 하나 추가하면 **여기도 같이 봐야 한다** — 빠뜨리면
     * 새 상태의 블록이 "끝난 것"으로 세어져 분석이 전사 도중에 시작된다.
     */
    private static final Set<SttBlockStatus> UNFINISHED_STATUSES =
            EnumSet.of(SttBlockStatus.PENDING, SttBlockStatus.QUEUED, SttBlockStatus.RUNNING);

    private final SpringDataSttBlockRepository sttBlockRepository;

    /*
     * 상태 전이 시각(startedAt · finishedAt)을 찍는다.
     *
     * ⚠ 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다 — 캡처 전용 Clock 빈을 새로 만들면 타입 주입이 모호해져 meeting
     * 도메인이 부팅에서 죽는다(AnalysisLayerPersistenceAdapter 가 같은 이유로 같은 주석을 달았다).
     */
    private final Clock clock;

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
    @Transactional(readOnly = true)
    public int countUnfinished(long meetingId) {
        return sttBlockRepository.countByMeetingIdAndStatusIn(meetingId, UNFINISHED_STATUSES);
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

    /*
     * 분석 관문(countUnfinished)과 **같은 집합을 쓴다.** 폴링이 볼 블록과 분석을 막는 블록이
     * 갈리면, 워커가 손대지 않는 상태의 블록 때문에 분석이 영구히 막히거나 그 반대가 된다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PendingBlock> findUnfinished(int limit) {
        return sttBlockRepository
                .findByStatusInOrderByIdAsc(UNFINISHED_STATUSES, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(entity -> new PendingBlock(
                        entity.getId(), entity.getMeetingId(), entity.getBlockSeq(),
                        entity.getStatus(), entity.getProvider(), entity.getProviderJobName(),
                        entity.getStartOffsetMs(), entity.getEndOffsetMs()))
                .toList();
    }

    @Override
    @Transactional
    public boolean markRunning(long blockId) {
        return transition(blockId, EnumSet.of(SttBlockStatus.QUEUED),
                entity -> entity.markRunning(LocalDateTime.now(clock)));
    }

    @Override
    @Transactional
    public boolean markDone(long blockId) {
        return transition(blockId, EnumSet.of(SttBlockStatus.QUEUED, SttBlockStatus.RUNNING),
                entity -> entity.markDone(LocalDateTime.now(clock)));
    }

    @Override
    @Transactional
    public boolean markFailed(long blockId, String errorCode) {
        return transition(blockId, EnumSet.of(SttBlockStatus.QUEUED, SttBlockStatus.RUNNING),
                entity -> entity.markFailed(errorCode, LocalDateTime.now(clock)));
    }

    /*
     * 허용된 상태에서만 옮긴다 — 조건을 조회에 넣어 DB 가 판정한다.
     *
     * 진 쪽이 false 를 받는다. 예외로 올리지 않는 이유는 이 경합이 **정상 동작**이기 때문이다 —
     * 사람이 재처리를 누른 직후 워커가 옛 잡의 결과를 들고 오는 순간이 그것이고, 그때 워커가
     * 물러나는 것이 맞다.
     */
    private boolean transition(long blockId, Set<SttBlockStatus> allowed,
                               java.util.function.Consumer<SttBlockJpaEntity> write) {
        Optional<SttBlockJpaEntity> claimed =
                sttBlockRepository.findWithLockByIdAndStatusIn(blockId, allowed);
        if (claimed.isEmpty()) {
            return false;
        }
        SttBlockJpaEntity entity = claimed.get();
        write.accept(entity);
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
                entity.getAudioS3Key(),
                entity.getStartedAt(),
                entity.getFinishedAt());
    }
}
