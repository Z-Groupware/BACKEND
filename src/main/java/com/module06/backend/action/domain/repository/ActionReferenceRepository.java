package com.module06.backend.action.domain.repository;

import java.time.LocalDate;
import java.util.List;

/* comment.
    액션이 참조하는 다른 애그리거트(회의 D, 프로젝트 C)의 값을 읽기 전용으로 가져오는 계약.
    domain이 선언하고 infrastructure가 구현한다 — project의 TeamReferenceRepository,
    cap의 MeetingReferenceRepository와 동일 패턴이다.

    존재 이유: ActionDistributionPort 계약이 teamId·parentActionId를 주지 않고 dueDate도
    null로 올 수 있어서, C가 회의·프로젝트에서 그 값을 유도해야 한다(결정로그 25번).
    application 서비스가 참조 엔티티(infrastructure)를 직접 만지면 계층 방향이 뒤집히므로
    이 계약을 사이에 둔다.

    두 조회 모두 배치다 — 회의 하나에서 액션 여러 건이 한꺼번에 들어오는 벌크 분배라
    건별로 조회하면 N+1이 그대로 터진다.

    이름에 Action 접두어를 붙인 이유: cap 도메인에 이미 MeetingReferenceRepository와
    그 @Component 구현체가 있어, 같은 이름을 쓰면 빈 이름이 충돌해 컨텍스트가 뜨지 않는다.

    연결된 클래스
    - ActionDistributionService        : 유일한 사용처 (application.service)
    - ActionReferenceRepositoryAdapter : JPA 구현체 (infrastructure.persistence)
*/
public interface ActionReferenceRepository {

    // 분배 입력의 sourceMeetingId로 TEAM 액션의 대상 팀·PERSONAL 액션의 상위 팀 액션을 찾는다.
    List<MeetingReference> findMeetingReferences(List<Long> meetingIds);

    // dueDate가 비어 들어온 액션의 마감일 기본값(프로젝트 마감일)을 찾는다.
    List<ProjectReference> findProjectReferences(List<Long> projectIds);

    // 수동 생성(FR-AC-01 예외 경로)에서 TEAM 액션의 teamId가 같은 회사 소속인지 검증한다
    // — 아니면 다른 회사 팀에 액션을 붙이는 IDOR이 된다.
    boolean existsTeamInCompany(Long teamId, Long companyId);

    // 수동 생성에서 PERSONAL 액션의 assigneeMemberId가 같은 회사 소속인지 검증한다.
    boolean existsMemberInCompany(Long memberId, Long companyId);

    // teamId는 OWNER 개설 회의면 null, relatedActionId는 팀 액션을 낳는 프로젝트 회의면 null이다.
    record MeetingReference(Long meetingId, Long teamId, Long relatedActionId) {
    }

    record ProjectReference(Long projectId, LocalDate dueDate) {
    }
}
