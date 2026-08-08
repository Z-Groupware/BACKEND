package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LockResult;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAnalysisRunJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataAnalysisLayerRepository;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingAnalysisRunRepository;

/*
 * 계층 잠금을 실제로 잡는 트랜잭션 하나. {@link AnalysisLayerPersistenceAdapter} 가 이 빈을 부른다.
 *
 * <h2>왜 어댑터와 분리했나 — 붙여 두면 INSERT 경합을 잡지 못한다</h2>
 * UNIQUE(meeting_id, layer) 충돌은 {@code save()} 자리에서 터지지 않는다. Hibernate 가 INSERT 를
 * **커밋 때까지 미루기** 때문에, 같은 메서드 안에서 try/catch 로 감싸도 예외는 그 catch 를
 * 지나쳐 트랜잭션 커밋에서 나온다. {@code saveAndFlush} 로 앞당겨 잡아도 그 트랜잭션은 이미
 * rollback-only 라, 정상 반환하면 커밋에서 UnexpectedRollbackException 이 난다.
 *
 * 그래서 트랜잭션 경계를 여기 두고, 잡는 것은 **경계 밖**(어댑터)에서 한다. 실행 번호 발급이
 * 같은 이유로 갈라져 있다({@link AnalysisRunSequenceIssuer}).
 *
 * <h2>왜 REQUIRES_NEW 인가</h2>
 * 잠금은 분석 트랜잭션과 생사를 같이 하면 안 된다. 커밋되지 않으면 RUNNING 이 남지 않아
 * 중복 방어가 성립하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AnalysisLayerLockAcquirer {

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

    /*
     * 계층을 RUNNING 으로 잡는다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         UNIQUE(meeting_id, layer) 충돌. 조회와 INSERT 사이에 다른 실행이 먼저 넣었을 때
     *         **커밋 시점에** 나온다. 호출자가 잠금 실패로 옮긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    LockResult acquire(long meetingId, LayerName layer, long runSeq) {
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
                /*
                 * RUNNING 이라고 다 살아 있는 것은 아니다(#177).
                 *
                 * 배포나 크래시로 잠근 프로세스가 사라지면 이 행은 그대로 남는데, 예전에는
                 * 그것도 "돌고 있음"으로 보고 물러났다 — 그 회의는 ANLZ-01 도 force 도
                 * ANLZ-02 도 통과하지 못해 **영원히 분석되지 않았다.**
                 *
                 * 그래서 심장이 뛰는지를 본다. 뛰고 있으면 지금까지와 같이 물러난다 —
                 * 그게 중복 방어이고, 여기서 잘못 회수하면 같은 회의를 두 번 태운다.
                 */
                if (!LayerLiveness.isStalled(entity, now)) {
                    // 다른 실행이 잡고 있다. 오류가 아니라 중복이 걸러진 것이다.
                    return LockResult.ALREADY_RUNNING;
                }
                /*
                 * 멈춘 잠금을 회수한다. **warn 으로 남긴다** — 정상 흐름이 아니라 앞선 실행이
                 * 끊겼다는 증거이고, 자주 찍히면 종료 대기(AnalysisAsyncConfig)가 짧다는 뜻이다.
                 * 조용히 되잡으면 서버가 죽었다는 사실이 어디에도 남지 않는다.
                 */
                log.warn("멈춘 계층 잠금을 회수한다 — meetingId={} layer={} 마지막생존={} 유예={}",
                        meetingId, layer.wireValue(), entity.lastAliveAt(), LayerLiveness.STALE_AFTER);
            }
            entity.restart(now);
            repository.save(entity);
            return LockResult.ACQUIRED;
        }

        repository.save(AnalysisLayerJpaEntity.running(meetingId, layer, now));
        return LockResult.ACQUIRED;
    }
}
