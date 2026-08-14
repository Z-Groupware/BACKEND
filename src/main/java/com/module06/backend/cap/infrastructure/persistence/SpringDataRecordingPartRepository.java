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

    // 블록 오디오 조립(ffmpeg)용 — 실제 s3Key·content_type이 필요해 전체 컬럼을 읽는다.
    // QUERY_002 준수 — 파생 쿼리(Between)로 표현.
    List<RecordingPartJpaEntity> findByMeetingIdAndSegmentSeqAndSeqBetweenOrderBySeqAsc(
            Long meetingId, Integer segmentSeq, Integer fromSeq, Integer toSeq);

    // 이 회의의 잔여 청크 조각 삭제(하드 삭제) — 파생 삭제 쿼리(QUERY_002 준수). 트랜잭션 안에서 호출.
    void deleteByMeetingId(Long meetingId);

    /*
     * TENANT_001 예외: 유일한 호출자 DeleteRecordingService.deleteRecording이 이 메서드를 부르기
     * 전에 이미 accessGuard.isSameCompany(meetingId, companyId)로 회사 스코프를 확인한다(line 76,
     * 101). meetingId만으로 다른 회사 행을 찾아 나설 경로가 없다 — CAP-15가 DB 행 삭제 전에 실제
     * s3Key를 읽어 S3 객체를 먼저 지우는 데 쓴다. 전체 컬럼이 필요해(s3Key) 프로젝션이 아니라
     * 엔티티 전체를 읽는다. 파생 쿼리(QUERY_002 준수).
     */
    // nosemgrep: tenant-derived-query-without-company-scope
    List<RecordingPartJpaEntity> findByMeetingId(Long meetingId);
}
