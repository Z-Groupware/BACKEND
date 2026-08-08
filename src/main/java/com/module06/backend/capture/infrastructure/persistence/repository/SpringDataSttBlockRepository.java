package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.SttBlockJpaEntity;

/* stt_block 접근. 조회는 블록 순번대로 준다 — 화면이 회의 진행 순서로 보여준다. */
public interface SpringDataSttBlockRepository extends JpaRepository<SttBlockJpaEntity, Long> {

    List<SttBlockJpaEntity> findByMeetingIdOrderByBlockSeqAsc(long meetingId);

    /*
     * 회의와 순번을 **함께** 조건에 넣는다. blockSeq 만으로 찾으면 다른 회의의 블록을 재처리할
     * 수 있다 — 관문(MeetingAccessGuard)은 회의까지만 본다.
     */
    Optional<SttBlockJpaEntity> findByMeetingIdAndBlockSeq(long meetingId, int blockSeq);
}
