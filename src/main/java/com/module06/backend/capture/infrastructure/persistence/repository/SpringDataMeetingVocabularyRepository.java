package com.module06.backend.capture.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.module06.backend.capture.domain.model.VocabularyStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingVocabularyJpaEntity;

/* meeting_vocabulary 접근. 회의당 하나다(UNIQUE(meeting_id)). */
public interface SpringDataMeetingVocabularyRepository
        extends JpaRepository<MeetingVocabularyJpaEntity, Long> {

    Optional<MeetingVocabularyJpaEntity> findByMeetingId(long meetingId);

    /*
     * 재생성 선점용. **쓰기 잠금을 걸고 읽는다** — 잠금 없이 읽으면 동시 요청이 둘 다
     * "PENDING 아님"을 보고 둘 다 선점해, 제공자에 어휘가 두 벌 만들어진다(계정 상한 낭비).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingVocabularyJpaEntity> findWithLockByMeetingId(long meetingId);

    /*
     * 완료 확인 폴링 대상 — 만드는 중이고 물어볼 이름이 있는 것만.
     *
     * 대기 이름이 NULL 인 PENDING 을 빼는 이유: 제출 전이거나 제출이 실패한 행이라 제공자에게
     * 물어볼 이름 자체가 없다. 담으면 워커가 매 주기 같은 행을 집어 아무것도 못 하고 돌아온다.
     */
    List<MeetingVocabularyJpaEntity> findByStatusAndPendingVocabularyNameIsNotNullOrderByIdAsc(
            VocabularyStatus status, Pageable pageable);

    /*
     * 승격·실패 전이 전용 — **쓰기 잠금을 걸고 읽는다.**
     *
     * 대기 이름 비교를 자바에서 하는데, 잠금이 없으면 두 폴링이 같은 스냅샷을 보고 둘 다
     * 통과한다. 잠금이 그 구간을 직렬화한다(markQueuedForRetry 와 같은 관용구).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingVocabularyJpaEntity> findWithLockById(long id);

    /* 밀려난 리소스가 남은 어휘(정리 대상). IX_MEETING_VOCABULARY_STALE 이 받는다. */
    List<MeetingVocabularyJpaEntity> findByStaleVocabularyNameIsNotNullOrderByIdAsc(Pageable pageable);

    /*
     * 응답 없이 오래 걸린 빌드(포기 대상).
     *
     * 접수 시각으로 판정한다 — created_at 은 행이 처음 생긴 시각이고(재생성이면 한참 전),
     * updated_at 은 ON UPDATE 가 없어 움직이지 않는다(V5.21).
     */
    List<MeetingVocabularyJpaEntity> findByStatusAndBuildStartedAtBeforeOrderByIdAsc(
            VocabularyStatus status, LocalDateTime startedBefore, Pageable pageable);

    /*
     * 정리 대상 — 아직 안 지웠고(deleted_at IS NULL) 끝난 어휘 중 지울 이름이 있는 것.
     *
     * IX_MEETING_VOCABULARY_CLEANUP(deleted_at, status)가 이 조회를 받는다 — V5.19 가 "그 조회는
     * 반드시 생긴다"며 미리 넣어 둔 인덱스다.
     */
    List<MeetingVocabularyJpaEntity>
            findByDeletedAtIsNullAndStatusInAndProviderVocabularyNameIsNotNullOrderByIdAsc(
                    Collection<VocabularyStatus> statuses, Pageable pageable);
}
