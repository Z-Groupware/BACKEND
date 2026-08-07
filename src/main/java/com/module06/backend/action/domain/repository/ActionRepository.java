package com.module06.backend.action.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.action.domain.model.Action;

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

    // FR-AC-02 — 개인 액션 목록(호출자 본인 소유분만).
    List<Action> findAllByAssigneeMemberId(Long assigneeMemberId);

    // FR-AC-02/03 — 상위 액션 표시값·벌크 상태변경 대상 배치 조회.
    List<Action> findAllByIds(List<Long> ids);

    /* 사람이 직접 추가한 액션을 지운다(RVW-04). AI 생성 액션은 이 경로로 오지 않는다 —
       지우면 review_log에 남길 판정 대상이 사라지고, 그건 반려(RVW-02)로 처리한다. */
    void delete(Action action);

    List<Action> findHandoverablePersonalActions(Long memberId, boolean includeDoneActions);

    List<Action> findTeamActionsByLeaderMemberId(Long leaderMemberId);

    // FR-AC-06 — 팀 액션 목록. JWT의 teamId로 이미 스코프된 값을 그대로 받아 그 팀의 TEAM 액션만 찾는다.
    List<Action> findAllByTeamId(Long teamId);

    // FR-AC-08 — 팀 액션 타임라인. 이 팀 액션(parentActionId) 아래 걸린, 같은 회사 소속 PERSONAL 액션 전체.
    List<Action> findAllByParentActionId(Long companyId, Long parentActionId);
}
