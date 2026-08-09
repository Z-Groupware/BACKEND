package com.module06.backend.cap.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 save/findById 등 기본 CRUD를 자동 구현해준다.
public interface SpringDataRecordingPartRepository extends JpaRepository<RecordingPartJpaEntity, Long> {

    // 현재 세그먼트의 청크 순번들. seq만 필요하므로 닫힌 프로젝션으로 seq 컬럼만 SELECT한다.
    // QUERY_002(신규 @Query 금지) 준수 — 파생 쿼리로 표현.
    List<SeqView> findByMeetingIdAndSegmentSeq(Long meetingId, Integer segmentSeq);

    /** seq 컬럼만 뽑는 닫힌 프로젝션 — missingSeqs 계산에 순번만 필요할 때 사용. */
    interface SeqView {
        Integer getSeq();
    }

    // 이 회의의 잔여 청크 조각 삭제(하드 삭제) — 파생 삭제 쿼리(QUERY_002 준수). 트랜잭션 안에서 호출.
    void deleteByMeetingId(Long meetingId);
}
