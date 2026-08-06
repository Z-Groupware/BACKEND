package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.domain.model.LayerName;
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
    public LockResult tryLock(long meetingId, LayerName layer, long runSeq) {
        try {
            return lockAcquirer.acquire(meetingId, layer, runSeq);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(meeting_id, layer) 충돌 — 조회와 INSERT 사이에 다른 실행이 먼저 넣었다.
            // 이 경합이 실제로 일어나는 자리라서 예외를 잠금 실패로 옮긴다. 여기서 터뜨리면
            // 정상적인 중복 방어가 장애로 보고된다.
            log.info("계층 잠금 경합 — meetingId={} layer={}", meetingId, layer.wireValue());
            return LockResult.ALREADY_RUNNING;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(long meetingId, LayerName layer, LayerRun run) {
        repository.findByMeetingIdAndLayer(meetingId, layer.wireValue())
                .ifPresent(entity -> {
                    entity.markDone(run, LocalDateTime.now(clock));
                    repository.save(entity);
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long meetingId, LayerName layer, String errorCode, String errorMessage,
                           LayerRun spent) {
        repository.findByMeetingIdAndLayer(meetingId, layer.wireValue())
                .ifPresent(entity -> {
                    entity.markFailed(errorCode, errorMessage, spent, LocalDateTime.now(clock));
                    repository.save(entity);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LayerState> findStates(long meetingId) {
        return repository.findByMeetingIdOrderByIdAsc(meetingId).stream()
                .map(entity -> new LayerState(
                        entity.layerName(), entity.getStatus(),
                        entity.getTokensIn(), entity.getTokensOut()))
                .toList();
    }
}
