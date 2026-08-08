package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.SttBlockJpaEntity;

/* stt_block 접근. 조회는 블록 순번대로 준다 — 화면이 회의 진행 순서로 보여준다. */
public interface SpringDataSttBlockRepository extends JpaRepository<SttBlockJpaEntity, Long> {

    List<SttBlockJpaEntity> findByMeetingIdOrderByBlockSeqAsc(long meetingId);

    /*
     * 회의와 순번을 **함께** 조건에 넣는다. blockSeq 만으로 찾으면 다른 회의의 블록을 재처리할
     * 수 있다 — 관문(MeetingAccessGuard)은 회의까지만 본다.
     */
    Optional<SttBlockJpaEntity> findByMeetingIdAndBlockSeq(long meetingId, int blockSeq);

    /*
     * 재처리 전이 전용 — **조건을 DB 가 판정하게 하고** 쓰기 잠금을 건다.
     *
     * <h2>id 로 찾아 자바에서 비교하면 안 된다</h2>
     * 같은 트랜잭션에서 이미 조회한 엔티티는 영속성 컨텍스트에 올라와 있고, {@code findById} 는
     * 잠금을 걸어도 **그 캐시된 인스턴스를 그대로 돌려준다** — DB 값이 그 사이 바뀌어도 자바
     * 쪽 필드는 옛 스냅샷이라, 상태·시도 횟수를 비교해봐야 자기가 읽은 값과 자기를 비교하는 것이
     * 된다. 두 요청이 여전히 같은 잡 이름을 만든다(CodeRabbit PR #223 지적).
     *
     * 조건을 **쿼리에** 넣으면 판정이 DB 에서 일어난다. 그 사이 누가 상태나 횟수를 바꿨으면
     * 이 조회가 비어서 돌아오고, 진 쪽은 거기서 멈춘다 — 그게 compare-and-set 이다.
     *
     * 파생 쿼리로 두는 이유는 조회 메서드들과 같다(Gate1 QUERY_002).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SttBlockJpaEntity> findWithLockByIdAndStatusAndRetryCount(
            long id, SttBlockStatus status, int retryCount);
}
