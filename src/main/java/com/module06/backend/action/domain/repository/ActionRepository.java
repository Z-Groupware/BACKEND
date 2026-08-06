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

    List<Action> findHandoverablePersonalActions(Long memberId, boolean includeDoneActions);

    List<Action> findTeamActionsByLeaderMemberId(Long leaderMemberId);
}
