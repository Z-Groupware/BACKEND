package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
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
     * 미완 블록 수(분석 시작 관문). 상태 목록을 인자로 받아 파생 쿼리로 둔다 — 어느 상태가
     * "미완"인지는 도메인의 판단이라 어댑터가 정하고, 여기는 세는 일만 한다(Gate1 QUERY_002).
     */
    int countByMeetingIdAndStatusIn(long meetingId, Collection<SttBlockStatus> statuses);

    /*
     * 미완 블록이 있는 회의를 배치로 찾는다(MEET-04 요약 상태).
     *
     * <h2>엔티티가 아니라 id 만 읽는다</h2>
     * 필요한 것은 "이 회의에 미완 블록이 있나"뿐이다. 엔티티를 읽으면 회의 20건 × 블록 수만큼
     * 행이 영속성 컨텍스트에 올라오는데, 그중 어느 필드도 쓰지 않는다.
     *
     * <h2>GROUP BY 를 쓰지 않는다</h2>
     * 파생 쿼리로는 집계를 못 하지만 집계가 필요하지도 않다 — 같은 회의가 여러 행으로 와도
     * 호출자가 Set 으로 접는다. @Query 를 새로 쓰지 않는 쪽을 고른 것이다(Gate1 QUERY_002).
     *
     * 어느 상태가 "미완"인지는 어댑터가 정한다(countByMeetingIdAndStatusIn 과 같은 규칙).
     */
    // TENANT_001 승인: 호출 경로가 SttBlockPersistenceAdapter.findMeetingsWithUnfinishedBlocks
    // 하나뿐이고, 그 위의 MeetingSummaryQueryService.findSummaryStatuses(:105) 가 이미
    // meetingAccessPort.filterInCompany(companyId, meetingIds) 로 남의 회사 회의를 떨어낸 뒤
    // accessible → targets(:120) → notStarted(:129) 로 좁혀 넘긴다. 이 메서드에 도달하는
    // meetingId 는 전부 요청 회사 것이다(같은 서비스 :59-60 에 "analysis_layer 에 company_id 가
    // 없어서 이 단계를 건너뛰면 회사 경계가 아예 없다"고 적혀 있는 그 필터다).
    //
    // ⚠ 이 근거는 호출 경로가 하나라는 데 기댄다. 포트(SttBlockRepository:65)가 companyId 를
    // 받지 않으므로 새 호출자가 필터 없이 부를 수 있다 — 진입점이 늘어나면 포트가 companyId 를
    // 받는 형태로 바꿀 것.
    // nosemgrep: review-loop.semgrep.tenant-derived-query-without-company-scope
    List<MeetingIdView> findByMeetingIdInAndStatusIn(
            Collection<Long> meetingIds, Collection<SttBlockStatus> statuses);

    /* 위 조회가 읽는 단 하나의 컬럼. */
    interface MeetingIdView {
        Long getMeetingId();
    }

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

    /*
     * 폴링 워커가 볼 블록 — 아직 끝나지 않은 것만. id 순으로 오래된 잡이 먼저 온다.
     *
     * 상한을 Pageable 로 받는다. 밀린 잡이 많을 때 한 주기가 끝나지 않으면 fixedDelay 의 겹침
     * 방어가 의미를 잃는다(TupleVectorSyncScheduler 주석과 같은 이유).
     */
    List<SttBlockJpaEntity> findByStatusInOrderByIdAsc(Collection<SttBlockStatus> statuses, Pageable pageable);

    /*
     * 상태 전이 전용 — **조건을 DB 가 판정하게 한다.**
     *
     * 폴링과 재처리(STT-04)가 같은 행을 동시에 만질 수 있다. id 로 읽어 자바에서 상태를
     * 비교하면 그 사이 바뀐 값이 안 보이고(findWithLockByIdAndStatusAndRetryCount 주석과 같은
     * 이유), 사람이 방금 QUEUED 로 되돌린 행을 워커가 옛 잡의 결과로 덮는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SttBlockJpaEntity> findWithLockByIdAndStatusIn(long id, Collection<SttBlockStatus> statuses);
}
