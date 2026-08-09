package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.OptionalLong;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AnalysisRunRepository;

/*
 * meeting_analysis_run 접근 어댑터다(#134).
 *
 * **이 클래스에는 트랜잭션이 없다.** 발급 자체는 {@link AnalysisRunSequenceIssuer} 가 자기
 * 트랜잭션에서 하고, 여기서는 그 경계 밖에서 경합만 걸러낸다 — 첫 행 INSERT 충돌은 커밋
 * 시점에 나오므로 발급과 같은 메서드 안에서는 잡을 수 없다(그쪽 주석에 자세히 적었다).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalysisRunPersistenceAdapter implements AnalysisRunRepository {

    private final AnalysisRunSequenceIssuer issuer;

    @Override
    public OptionalLong begin(long meetingId) {
        try {
            return OptionalLong.of(issuer.issue(meetingId));
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            /*
             * 첫 행을 만드는 경합에서 졌다. 첫 실행에서만 생긴다(행이 생긴 뒤로는 잠금이 줄을 세운다).
             *
             * 두 종류로 온다 —
             *   DataIntegrityViolationException  PK 충돌. 상대가 먼저 커밋했다
             *   ConcurrencyFailureException      잠금 경합. InnoDB 는 없는 행에 대한
             *                                    SELECT ... FOR UPDATE 에 갭 락을 걸어서, 두
             *                                    실행이 서로의 INSERT 를 막아 데드락으로 갈린다
             *                                    (진 쪽은 CannotAcquireLockException 이다)
             *
             * 뒤쪽을 안 잡으면 정상적인 경합이 500 으로 보고된다 — H2 에서는 갭 락이 없어
             * 앞쪽만 나오므로 테스트로는 드러나지 않는다.
             *
             * 다시 시도하지 않는다. **물러나는 것이 맞다** — 먼저 넣은 실행의 번호는 우리 것보다
             * 크거나 같으므로 그쪽이 최신이다. 재시도해서 더 큰 번호를 받아내면 우리가 늦게
             * 시작했다는 사실을 지우고 최신으로 위장하게 된다.
             */
            log.info("실행 번호 발급 경합 — 다른 실행이 먼저 시작했다. meetingId={} 사유={}",
                    meetingId, e.getClass().getSimpleName());
            return OptionalLong.empty();
        }
    }
}
