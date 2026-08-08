package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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
     * 재처리 전이 전용 — **쓰기 잠금을 걸고** 읽는다.
     *
     * 잠금 없이 읽으면 두 요청이 같은 FAILED 스냅샷을 보고 같은 retryCount 로 **같은 잡 이름을
     * 만들어 둘 다 제출한다.** 계정 내 중복 이름이라 두 번째가 거절되는데, 그게 이 코드가 이름에
     * 횟수를 넣어 막으려던 상황이다. 행 잠금이 그 구간을 직렬화한다
     * (analysis_layer 의 계층 잠금과 같은 방식이다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SttBlockJpaEntity> findWithLockById(long id);
}
