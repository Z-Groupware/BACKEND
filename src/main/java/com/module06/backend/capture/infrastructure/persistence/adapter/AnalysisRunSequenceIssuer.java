package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAnalysisRunJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingAnalysisRunRepository;

/*
 * 실행 번호를 실제로 올리는 트랜잭션 하나(#134). {@link AnalysisRunPersistenceAdapter} 가
 * 이 빈을 부른다.
 *
 * <h2>왜 어댑터와 분리했나 — 붙여 두면 경합을 잡지 못한다</h2>
 * 첫 행 INSERT 경합(PK 충돌)은 {@code save()} 자리에서 터지지 않는다. Hibernate 가 INSERT 를
 * **커밋 때까지 미루기** 때문에, 같은 메서드 안에서 try/catch 로 감싸도 예외는 그 catch 를
 * 지나쳐 트랜잭션 커밋에서 나온다. 실제로 그렇게 짰다가 테스트에서 잡혔다.
 *
 * 그래서 트랜잭션 경계를 여기 두고, 잡는 것은 **경계 밖**(어댑터)에서 한다. 커밋이 이 메서드를
 * 벗어나는 순간에 일어나므로 호출자에게는 평범한 예외로 보인다.
 *
 * <h2>왜 REQUIRES_NEW 인가</h2>
 * 발급이 분석 트랜잭션과 생사를 같이 하면 안 된다. 계층이 실패해 바깥이 롤백되면 발급된
 * 번호까지 사라지고, 그러면 뒤늦게 깨어난 오래된 실행이 자기가 최신이라고 판정한다 —
 * 이 테이블을 만든 이유가 통째로 없어진다.
 */
@Component
@RequiredArgsConstructor
class AnalysisRunSequenceIssuer {

    private final SpringDataMeetingAnalysisRunRepository repository;

    /*
     * 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다. 캡처 전용 Clock 빈을 새로 만들면 안 된다.
     */
    private final Clock clock;

    /*
     * 이 회의의 다음 실행 번호를 발급한다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         첫 행을 만드는 경합에서 졌을 때. 커밋 시점에 나온다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    long issue(long meetingId) {
        LocalDateTime now = LocalDateTime.now(clock);

        MeetingAnalysisRunJpaEntity entity = repository.findWithLockByMeetingId(meetingId)
                .orElseGet(() -> MeetingAnalysisRunJpaEntity.initial(meetingId, now));
        long issued = entity.issueNext(now);
        repository.save(entity);
        return issued;
    }
}
