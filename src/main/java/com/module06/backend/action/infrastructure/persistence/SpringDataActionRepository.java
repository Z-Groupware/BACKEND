package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.action.domain.model.ActionReviewStatus;
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

    // FR-AC-08 — 팀 액션 타임라인. parentActionId만으로 찾으면 DB가 강제 안 하는 회사·종류
    // 불변식(자식은 항상 부모와 같은 회사의 PERSONAL)에 기대게 된다 — companyId·actionType도
    // 조건에 넣어 조회 자체에서 보장한다(2026-08-07, CodeRabbit 지적).
    List<ActionJpaEntity> findAllByActionTypeAndCompanyIdAndParentActionId(
            ActionType actionType, Long companyId, Long parentActionId);

    // 마이페이지 확정 대기 목록(MeetingActionQueryPort) — 아직 분배되지 않은(dispatched_at IS NULL)
    // 액션이 남은 회의를 찾는다. review_status = PENDING만 보면 틀린다 — HUMAN_CONFIRMED인데
    // 아직 분배 확정 버튼을 안 누른 액션을 놓친다(V5.17__add_dispatched_at_to_action.sql:12-17).
    // REJECTED는 분배에서 skip되어 dispatched_at이 영원히 NULL로 남으므로 제외해야 무한 잔류를 막는다.
    //
    // 프로젝션인 이유: 회의당 몇 건이 미분배인지만 필요한데 엔티티를 통째로 읽으면 description
    // (TEXT)·gate_signals(json)까지 전건 메모리에 올라온다(identity/member의
    // SpringDataMemberRepository:41-56과 동일한 이유). 집계(COUNT GROUP BY)는 CI Gate 1
    // Semgrep(QUERY_002)이 신규 @Query를 막아 SQL로 못 하고, 행을 읽어 자바에서 집계한다.
    List<UndispatchedProjection> findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(
            Long companyId, List<Long> sourceMeetingIds, ActionReviewStatus excludedReviewStatus);

    boolean existsByCompanyIdAndId(Long companyId, Long id);

    // FR-AC-09 — 회의별 액션 조회. actionType 조건이 없다 — TEAM·PERSONAL이 섞여 나오는 게
    // 이 조회의 목적이다(회의 상세 화면 전용, TeamActionService 쪽 조회들과 다른 이유).
    List<ActionJpaEntity> findAllByCompanyIdAndSourceMeetingId(Long companyId, Long sourceMeetingId);

    // 닫힌 프로젝션 — sourceMeetingId 한 컬럼만 읽는다.
    interface UndispatchedProjection {
        Long getSourceMeetingId();
    }
}
