package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

/* comment.
    action 테이블용 Spring Data JPA 인터페이스. 구현 시 JpaRepository를 상속한다.
    도메인 계층은 이 인터페이스를 모른다 — 어댑터만 안다.
    개인 액션 목록(assignee 스코프)·팀 액션 목록(teamId 스코프)·타임라인(parentActionId 기준)이
    모두 이 인터페이스의 쿼리 메서드로 갈린다 — N+1이 터지기 쉬운 지점이라 fetch join / projection 검토 필요.
    인수인계 고아경보 소스(findDistinctParentTeamActionsByAssignee)는 parent_action_id self-join으로
    퇴사자 개인 액션의 부모 TEAM 액션을 distinct로 뽑는다.

    연결된 클래스
    - ActionJpaEntity          : 다루는 엔티티
    - ActionPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataActionRepository extends JpaRepository<ActionJpaEntity, Long> {

    List<ActionJpaEntity> findAllByAssigneeMemberIdAndActionTypeAndStatusNotOrderByDueDateAscIdAsc(
            Long assigneeMemberId,
            ActionType actionType,
            ActionStatus status
    );

    List<ActionJpaEntity> findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(
            Long assigneeMemberId,
            ActionType actionType
    );

    @Query("""
            select distinct parent
            from ActionJpaEntity personal, ActionJpaEntity parent
            where personal.assigneeMemberId = :memberId
              and personal.actionType = com.module06.backend.action.domain.model.ActionType.PERSONAL
              and parent.id = personal.parentActionId
              and parent.actionType = com.module06.backend.action.domain.model.ActionType.TEAM
            order by parent.id asc
            """)
    List<ActionJpaEntity> findDistinctParentTeamActionsByAssignee(@Param("memberId") Long memberId);
}
