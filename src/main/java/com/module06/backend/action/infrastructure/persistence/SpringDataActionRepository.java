package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.action.domain.model.ActionType;

/* comment.
    action 테이블용 Spring Data JPA 인터페이스. 도메인 계층은 이 인터페이스를 모른다 — 어댑터만 안다.
    개인 액션 목록(assignee 스코프)·팀 액션 목록(teamId 스코프)·타임라인(parentActionId 기준) 등
    나머지 조회는 각 유스케이스 착수 시 추가 — N+1이 터지기 쉬운 지점이라 fetch join/projection 검토 필요.

    이번 슬라이스(ActionReassignPort)엔 인수인계용 두 조회만 필요. 원래 ProjectReferenceEntity·
    ActionTeamReferenceEntity와 JPQL로 직접 조인했으나, CI Gate 1(Semgrep QUERY_002, 신규 @Query
    금지)에 걸려 파생 쿼리로 대체(2026-08-06) — 프로젝트 완료여부·팀장 소속 필터링은
    ActionPersistenceAdapter가 두 단계 조회로 자바 레벨에서 처리한다.

    연결된 클래스
    - ActionJpaEntity          : 다루는 엔티티
    - ActionPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataActionRepository extends JpaRepository<ActionJpaEntity, Long> {

    List<ActionJpaEntity> findAllByActionTypeAndAssigneeMemberId(ActionType actionType, Long assigneeMemberId);

    List<ActionJpaEntity> findAllByActionTypeAndTeamIdIn(ActionType actionType, List<Long> teamIds);

    List<ActionJpaEntity> findAllByActionTypeAndProjectId(ActionType actionType, Long projectId);

    // FR-AC-08 — 팀 액션 타임라인. 이 팀 액션 아래 걸린 하위 PERSONAL 액션 전체.
    List<ActionJpaEntity> findAllByParentActionId(Long parentActionId);
}
