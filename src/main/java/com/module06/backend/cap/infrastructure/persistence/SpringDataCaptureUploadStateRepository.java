package com.module06.backend.cap.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 save/findById 등 기본 CRUD를 자동 구현해준다.
public interface SpringDataCaptureUploadStateRepository
        extends JpaRepository<CaptureUploadStateJpaEntity, Long> {

    // 주어진 회의들 중 캡처가 존재하는 가장 최근(created_at 최신) 상태 하나. 한 사람이 여러 진행 중
    // 회의에 겹쳐 있는 드문 경우를 대비해 가장 최근에 시작된 캡처를 고른다. 파생 쿼리(QUERY_002 준수).
    Optional<CaptureUploadStateJpaEntity> findFirstByMeetingIdInOrderByCreatedAtDesc(Collection<Long> meetingIds);

    // meetingId로 파생 삭제 — CrudRepository.deleteById(meetingId)를 쓰면 행이 없을 때
    // EmptyResultDataAccessException을 던진다(최종 리뷰에서 발견). 이 회의에 캡처 상태 행이
    // 애초에 없는 경우(CAP-10 수동 업로드는 presign을 한 번도 안 타 상태 행이 없음)나, 조립
    // 완료 때 이미 지워진 뒤 삭제(CAP-15)가 다시 부르는 경우 둘 다 실제로 발생한다 — 파생 삭제
    // 쿼리는 RecordingPartRepository·RecordingRepository의 deleteByMeetingId와 동일하게 대상이
    // 0건이어도 조용히 넘어간다(QUERY_002 준수).
    void deleteByMeetingId(Long meetingId);

    /*
     * 블록 순번 예약(CAS) 전용 — SttBlockPersistenceAdapter.findWithLockByIdAndStatusAndRetryCount와
     * 같은 패턴이다. meetingId(=PK)와 blocksFormed를 **조건에 같이 넣어** DB가 판정하게 하고,
     * 쓰기 잠금을 걸어 조회~갱신 사이 경합을 막는다. blocksFormed가 그 사이 바뀌었으면 이 조회
     * 자체가 비어서 돌아온다(파생 쿼리, QUERY_002 준수).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CaptureUploadStateJpaEntity> findWithLockByMeetingIdAndBlocksFormed(Long meetingId, int blocksFormed);
}
