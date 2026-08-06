package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
    action 테이블용 Spring Data JPA 인터페이스. 도메인 계층은 이 인터페이스를 모른다 — 어댑터만 안다.
    개인 액션 목록(assignee 스코프)·팀 액션 목록(teamId 스코프)·타임라인(parentActionId 기준) 등
    나머지 조회는 각 유스케이스 착수 시 추가 — N+1이 터지기 쉬운 지점이라 fetch join/projection 검토 필요.

    이번 슬라이스(ActionReassignPort)엔 인수인계용 두 조회만 필요:
    - findHandoverablePersonalActions: ProjectReferenceEntity와 조인해 완료된 프로젝트 제외
    - findTeamActionsByLeaderMemberId: TeamReferenceEntity와 조인해 팀장 소속 팀만

    연결된 클래스
    - ActionJpaEntity          : 다루는 엔티티
    - ActionPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataActionRepository extends JpaRepository<ActionJpaEntity, Long> {

    @Query("""
            SELECT a FROM ActionJpaEntity a, ProjectReferenceEntity p
            WHERE a.projectId = p.id
              AND a.actionType = com.module06.backend.action.domain.model.ActionType.PERSONAL
              AND a.assigneeMemberId = :memberId
              AND p.status <> com.module06.backend.project.domain.model.ProjectStatus.DONE
              AND (:includeDoneActions = true OR a.status <> com.module06.backend.action.domain.model.ActionStatus.DONE)
            """)
    List<ActionJpaEntity> findHandoverablePersonalActions(
            @Param("memberId") Long memberId,
            @Param("includeDoneActions") boolean includeDoneActions
    );

    @Query("""
            SELECT a FROM ActionJpaEntity a, ActionTeamReferenceEntity t
            WHERE a.teamId = t.id
              AND a.actionType = com.module06.backend.action.domain.model.ActionType.TEAM
              AND t.leaderMemberId = :leaderMemberId
            """)
    List<ActionJpaEntity> findTeamActionsByLeaderMemberId(@Param("leaderMemberId") Long leaderMemberId);
}
