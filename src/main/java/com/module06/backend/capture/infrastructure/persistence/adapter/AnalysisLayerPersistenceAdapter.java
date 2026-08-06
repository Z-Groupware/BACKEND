package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAnalysisRunJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataAnalysisLayerRepository;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingAnalysisRunRepository;

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
     * 실행 순서 확인용이다(#134). 포트(AnalysisRunRepository)가 아니라 Spring Data 저장소를
     * 직접 받는다 — 확인이 잠금과 **같은 트랜잭션** 안에 있어야 원자적인데, 포트 구현은
     * REQUIRES_NEW 로 자기 트랜잭션을 열어 그 조건이 깨진다.
     */
    private final SpringDataMeetingAnalysisRunRepository runRepository;

    /*
     * 시각은 주입받는다 — 기록을 테스트에서 고정할 수 있어야 한다.
     *
     * ⚠ 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다. 캡처 전용 Clock 빈을 새로 만들면 안 된다 — 두 개가 되는 순간
     * 타입 주입이 모호해져 meeting 도메인 서비스들이 부팅에서 죽는다.
     */
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LockResult tryLock(long meetingId, LayerName layer, long runSeq) {
        LocalDateTime now = LocalDateTime.now(clock);

        /*
         * 순서 확인이 먼저다(#134). 실행 번호 행에도 쓰기 잠금을 걸고 읽으므로, 번호 발급과
         * 이 확인이 같은 행 위에서 줄을 선다 — 발급이 커밋된 뒤에야 이 읽기가 그 값을 본다.
         *
         * 행이 없으면 통과시킨다. 이 마이그레이션 이전부터 돌던 실행이나 테스트 경로가 그 모양이고,
         * 여기서 막으면 순서 문제가 아닌 상황에서 분석이 전부 멈춘다.
         */
        long current = runRepository.findWithLockByMeetingId(meetingId)
                .map(MeetingAnalysisRunJpaEntity::getRunSeq)
                .orElse(0L);
        if (runSeq < current) {
            log.info("실행이 밀렸다 — 더 나중 실행이 있어 이 계층을 잡지 않는다. "
                    + "meetingId={} layer={} 내번호={} 최신={}",
                    meetingId, layer.wireValue(), runSeq, current);
            return LockResult.SUPERSEDED;
        }

        /*
         * 기존 행이면 **쓰기 잠금을 걸고** 읽어 상태를 본다. 잠금 없이 읽으면 두 실행이 같은
         * "RUNNING 아님"을 보고 둘 다 잠근 것으로 판단해, 같은 계층을 두 번 돌려 토큰이
         * 그대로 두 배가 된다. 행 잠금이 그 구간을 직렬화한다.
         *
         * DONE 이어도 잠근다. ANLZ-01 강제 재실행이 그 경로이고, "이미 완료" 판정은
         * 유스케이스가 하지 이 어댑터가 하지 않는다.
         */
        Optional<AnalysisLayerJpaEntity> existing =
                repository.findWithLockByMeetingIdAndLayer(meetingId, layer.wireValue());
        if (existing.isPresent()) {
            AnalysisLayerJpaEntity entity = existing.get();
            if (entity.getStatus() == LayerStatus.RUNNING) {
                // 다른 실행이 잡고 있다. 오류가 아니라 중복이 걸러진 것이다.
                return LockResult.ALREADY_RUNNING;
            }
            entity.restart(now);
            repository.save(entity);
            return LockResult.ACQUIRED;
        }

        try {
            repository.save(AnalysisLayerJpaEntity.running(meetingId, layer, now));
            return LockResult.ACQUIRED;
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
