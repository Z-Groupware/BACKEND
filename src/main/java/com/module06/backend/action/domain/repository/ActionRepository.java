package com.module06.backend.action.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.action.domain.model.Action;

/* comment.
    action 저장소 계약. 이번 슬라이스(ActionReassignPort 배선)에 필요한 메서드만 우선 채운다 —
    나머지 조회(개인/팀 목록, 회의별 조회 등)는 각 유스케이스 착수 시 추가.

    findHandoverablePersonalActions: 인수인계 대상 개인 액션 조회. includeDoneActions=false면
    미완료(status != DONE)만, true면 상태 무관 전체 — 어느 쪽이든 완료된(status=DONE) 프로젝트에
    속한 액션은 항상 제외한다(2026-08-06 종준 PO 확인).

    findTeamActionsByLeaderMemberId: 이 memberId가 팀장인 팀들의 TEAM 액션 전체 —
    인수인계(E)의 퇴사자 팀 액션 고아경보(orphan-alert)용, 읽기 전용.
*/
public interface ActionRepository {

    Action save(Action action);

    Optional<Action> findById(Long id);

    List<Action> findHandoverablePersonalActions(Long memberId, boolean includeDoneActions);

    List<Action> findTeamActionsByLeaderMemberId(Long leaderMemberId);
}
