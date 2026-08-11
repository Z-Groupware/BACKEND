package com.module06.backend.action.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    action 저장소 계약. 착수한 슬라이스에 필요한 메서드만 채워 나간다 —
    나머지 조회(개인/팀 목록, 회의별 조회 등)는 각 유스케이스 착수 시 추가.

    saveAll: AI 분배(ActionDistributionPort)는 회의 하나에서 액션 여러 건이 한 번에 들어오는
    벌크 생성이라 건별 save 대신 일괄 저장을 쓴다. 반환 순서는 입력 순서를 유지해야 한다 —
    호출자가 채번된 actionId를 원본 분배 입력과 짝지어 돌려주기 때문(DistributedAction).

    findHandoverablePersonalActions: 인수인계 대상 개인 액션 조회. includeDoneActions=false면
    미완료(status != DONE)만, true면 상태 무관 전체 — 어느 쪽이든 완료된(status=DONE) 프로젝트에
    속한 액션은 항상 제외한다(2026-08-06 종준 PO 확인).

    findTeamActionsByLeaderMemberId: 이 memberId가 팀장인 팀들의 TEAM 액션 전체 —
    인수인계(E)의 퇴사자 팀 액션 고아경보(orphan-alert)용, 읽기 전용.
*/
public interface ActionRepository {

    Action save(Action action);

    List<Action> saveAll(List<Action> actions);

    Optional<Action> findById(Long id);

    /* 쓰기 잠금을 걸고 읽는다(SELECT ... FOR UPDATE) — 읽은 뒤 곧바로 같은 트랜잭션에서 다시
       쓰는 read-modify-write 경로 전용이다(ActionReassignAdapter.reassign()). 잠금 없이
       읽으면 동시 요청 둘이 같은 값을 읽고 나중에 쓴 쪽이 먼저 쓴 쪽을 조용히 덮어쓴다 —
       담당자 재배정처럼 "누가 최종 담당자인가"가 중요한 경로에서 유실이 생긴다. */
    Optional<Action> findByIdForUpdate(Long id);

    // FR-AC-02 — 개인 액션 목록(호출자 본인 소유분만). 캘린더(CalendarQueryService)가 월간
    // 집계에 전건이 필요해서 그대로 쓰고 있다 — 페이지네이션 버전은 별도 메서드로 추가한다
    // (2026-08-10, 이홍근 요청).
    List<Action> findAllByAssigneeMemberId(Long assigneeMemberId);

    // 2026-08-10 필터/정렬 추가(이홍근 요청) — status·overdue는 null이면 필터 안 함.
    // overdue=true면 지연(진행중+마감일<오늘)만, false면 지연 아닌 것만.
    List<Action> findAllByAssigneeMemberId(
            Long assigneeMemberId, ActionStatus status, Boolean overdue, String sort, String order, int page, int size);

    long countByAssigneeMemberId(Long assigneeMemberId, ActionStatus status, Boolean overdue);

    // FR-AC-02/03 — 상위 액션 표시값·벌크 상태변경 대상 배치 조회.
    List<Action> findAllByIds(List<Long> ids);

    /* 사람이 직접 추가한 액션을 지운다(RVW-04). AI 생성 액션은 이 경로로 오지 않는다 —
       지우면 review_log에 남길 판정 대상이 사라지고, 그건 반려(RVW-02)로 처리한다. */
    void delete(Action action);

    List<Action> findHandoverablePersonalActions(Long memberId, boolean includeDoneActions);

    List<Action> findTeamActionsByLeaderMemberId(Long leaderMemberId);

    // FR-AC-06 — 팀 액션 목록. JWT의 teamId로 이미 스코프된 값을 그대로 받아 그 팀의 TEAM 액션만 찾는다.
    // 2026-08-10 페이지네이션 도입(이홍근 요청) — 기존 전건 조회는 다른 호출자가 없어 페이지네이션
    // 버전으로 교체했다(Project.findAllByCompanyId와 동일 판단).
    List<Action> findAllByTeamId(Long teamId, ActionStatus status, String sort, String order, int page, int size);

    long countByTeamId(Long teamId, ActionStatus status);

    // FR-AC-08 — 팀 액션 타임라인. 이 팀 액션(parentActionId) 아래 걸린, 같은 회사 소속 PERSONAL 액션 전체.
    List<Action> findAllByParentActionId(Long companyId, Long parentActionId);

    // FR-AC-09 — 회의별 액션 조회. TEAM·PERSONAL이 actionType으로 섞여 나온다(회의 상세 화면 전용).
    List<Action> findAllByCompanyIdAndSourceMeetingId(Long companyId, Long sourceMeetingId);

    // 2026-08-11, 이슈 #355(이홍근 요청) — 팀 액션 목록의 하위 개인 액션 진척 게이지
    // ("3/5")용 배치 집계. 팀 액션 하나당 하위 PERSONAL 액션의 전체 건수·완료 건수.
    // findAllByParentActionId(단건, FR-AC-08 타임라인용)와 달리 목록 페이지의 여러 팀 액션
    // id를 한 번에 묶어 N+1을 피한다. companyId는 CodeRabbit(#357) 지적 반영 — parentActionId만
    // 조건이면 다른 회사의 PERSONAL 액션이 같은 parentActionId(같은 물리 테이블의 auto-increment
    // id라 우연히 겹칠 수 있다)를 참조할 때 회사 경계 밖 데이터가 섞여 들어간다.
    // findAllByParentActionId가 이미 companyId를 필수로 받는 것과 동일한 이유.
    List<ChildActionProgress> countChildActionProgressByParentActionIds(Long companyId, List<Long> parentActionIds);

    record ChildActionProgress(Long parentActionId, int totalCount, int doneCount) {
    }
}
