package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataAnalysisLayerRepository;

/*
 * analysis_layer 접근 어댑터다.
 *
 * <h2>왜 REQUIRES_NEW 인가</h2>
 * 상태 기록은 분석 트랜잭션과 **생사를 같이 하면 안 된다.** 계층이 실패해 바깥 트랜잭션이
 * 롤백되면 FAILED 기록까지 함께 사라지고, 그러면 "실패했는데 아무 흔적이 없는" 상태가 된다.
 * 이 저장소가 이미 겪은 실패 모드다 — 판정이 매번 크래시하는데 감사 로그가 0건이었다.
 * 잠금(RUNNING)도 마찬가지다. 커밋되지 않으면 중복 방어가 성립하지 않는다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalysisLayerPersistenceAdapter implements AnalysisLayerRepository {

    private final SpringDataAnalysisLayerRepository repository;

    /*
     * 잠금 획득은 이 빈이 자기 트랜잭션에서 한다. 여기서는 그 경계 밖에서 INSERT 경합만
     * 걸러낸다 — 붙여 두면 잡히지 않는 이유는 그쪽 주석에 적었다.
     */
    private final AnalysisLayerLockAcquirer lockAcquirer;

    /*
     * 시각은 주입받는다 — 기록을 테스트에서 고정할 수 있어야 한다.
     *
     * ⚠ 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다. 캡처 전용 Clock 빈을 새로 만들면 안 된다 — 두 개가 되는 순간
     * 타입 주입이 모호해져 meeting 도메인 서비스들이 부팅에서 죽는다.
     */
    private final Clock clock;

    /*
     * **트랜잭션이 없다.** 잠금은 lockAcquirer 가 자기 트랜잭션에서 잡고, 여기서는 그 경계
     * 밖에서 INSERT 경합만 걸러낸다 — 커밋 시점에 나오는 예외라 안쪽에서는 잡히지 않는다.
     */
    @Override
    public LockOutcome tryLock(long meetingId, LayerName layer, long runSeq) {
        try {
            return lockAcquirer.acquire(meetingId, layer, runSeq);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(meeting_id, layer) 충돌 — 조회와 INSERT 사이에 다른 실행이 먼저 넣었다.
            // 이 경합이 실제로 일어나는 자리라서 예외를 잠금 실패로 옮긴다. 여기서 터뜨리면
            // 정상적인 중복 방어가 장애로 보고된다.
            log.info("계층 잠금 경합 — meetingId={} layer={}", meetingId, layer.wireValue());
            return LockOutcome.of(LockResult.ALREADY_RUNNING);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(long meetingId, LayerName layer, int attempt, LayerRun run) {
        withOwnedLock(meetingId, layer, attempt, "완료 기록",
                entity -> entity.markDone(run, LocalDateTime.now(clock)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long meetingId, LayerName layer, int attempt, String errorCode,
                           String errorMessage, LayerRun spent) {
        withOwnedLock(meetingId, layer, attempt, "실패 기록",
                entity -> entity.markFailed(errorCode, errorMessage, spent, LocalDateTime.now(clock)));
    }

    /*
     * **내가 잡은 잠금일 때만** 쓴다(#212).
     *
     * 회수(#177)가 생기면서 잠금을 뺏기는 실행이 존재하게 됐다. 뺏긴 쪽이 뒤늦게 상태를 쓰면
     * 새 주인이 아직 돌고 있는 계층이 DONE·FAILED 로 닫히고, 닫힌 계층은 다시 잠글 수 있으므로
     * **제3의 실행이 새 주인과 동시에 같은 계층을 돌게 된다.** 그 앞을 여기서 막는다.
     *
     * 예외를 올리지 않고 warn 으로 넘긴다 — 뺏긴 실행은 이미 밀린 실행이고, 정리하다가 터뜨리면
     * 그 실행의 로그가 실패로 뒤덮여 **정작 뺏긴 원인(심장이 멈췄던 것)을 찾기 어려워진다.**
     * 무시했다는 사실 자체는 남겨야 하므로 조용히 지나가지도 않는다.
     */
    private void withOwnedLock(long meetingId, LayerName layer, int attempt, String what,
                               java.util.function.Consumer<AnalysisLayerJpaEntity> write) {
        repository.findByMeetingIdAndLayer(meetingId, layer.wireValue())
                .ifPresent(entity -> {
                    if (entity.getAttemptCount() != attempt) {
                        log.warn("{} 무시 — 이 잠금의 주인이 아니다. meetingId={} layer={} 내번호={} 현재주인={}",
                                what, meetingId, layer.wireValue(), attempt, entity.getAttemptCount());
                        return;
                    }
                    write.accept(entity);
                    repository.save(entity);
                });
    }

    /*
     * 심장을 한 번 찍는다(#177).
     *
     * RUNNING 일 때만 쓴다. 이미 닫힌(DONE·FAILED) 계층에 찍으면 늦게 도착한 갱신이 끝난
     * 계층을 살아 있는 것처럼 만들고, 그 행은 회수 대상에서도 빠진다.
     *
     * REQUIRES_NEW 인 이유는 이 어댑터의 다른 쓰기와 같다 — 분석 트랜잭션과 생사를 같이 하면
     * 롤백될 때 심장 기록도 함께 사라진다. 게다가 이 값은 **분석이 도는 중에** 다른 실행에게
     * 보여야 하므로, 끝날 때 한꺼번에 커밋되면 아무 소용이 없다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(long meetingId, LayerName layer, int attempt) {
        repository.findByMeetingIdAndLayer(meetingId, layer.wireValue())
                .filter(entity -> entity.getStatus() == LayerStatus.RUNNING)
                /*
                 * 내가 잡은 잠금일 때만 찍는다(#212). 뺏긴 실행이 새 주인의 심장을 대신 찍으면
                 * **정작 새 주인이 죽었을 때 회수가 막힌다** — 회수 장치를 회수 못 하게 만든다.
                 */
                .filter(entity -> entity.getAttemptCount() == attempt)
                .ifPresent(entity -> {
                    entity.touch(LocalDateTime.now(clock));
                    repository.save(entity);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LayerState> findStates(long meetingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByMeetingIdOrderByIdAsc(meetingId).stream()
                .map(entity -> toState(entity, now))
                .toList();
    }

    /*
     * 배치 판정도 `now` 를 **한 번만** 읽는다.
     *
     * 회의마다 시각을 다시 읽으면 같은 응답 안에서 기준선이 밀린다 — 앞 회의는 아직 살아 있고
     * 뒤 회의는 멈춘 것으로 판정될 수 있고, 그 차이가 목록을 새로 고칠 때마다 흔들린다.
     * 한 번 읽은 시각으로 전부 재는 것이 같은 화면에서 같은 답을 준다.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<LayerState>> findStatesByMeetings(List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, List<LayerState>> byMeeting = new LinkedHashMap<>();
        for (AnalysisLayerJpaEntity entity : repository.findByMeetingIdInOrderByMeetingIdAscIdAsc(meetingIds)) {
            byMeeting.computeIfAbsent(entity.getMeetingId(), id -> new ArrayList<>())
                    .add(toState(entity, now));
        }
        return byMeeting;
    }

    private static LayerState toState(AnalysisLayerJpaEntity entity, LocalDateTime now) {
        return new LayerState(
                entity.layerName(), entity.getStatus(),
                entity.getTokensIn(), entity.getTokensOut(),
                // 잠금을 회수하는 쪽과 **같은 기준**을 쓴다. 갈리면 잠금은 풀렸는데
                // 화면은 「AI 처리 중」이거나, 그 반대가 된다.
                LayerLiveness.isStalled(entity, now));
    }
}
