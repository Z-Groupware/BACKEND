package com.module06.backend.action.domain.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // 2026-08-11 — 팀장이 다른 팀원의 개인 액션 목록을 조회할 때(팀원 관리 화면), 그 팀원이
    // 자기 팀 소속인지 확인한다. 아니면 팀장이 다른 팀 팀원의 액션까지 조회하는 IDOR이 된다.
    boolean existsMemberInTeam(Long memberId, Long teamId);

    // FR-AC-02 — 개인 액션 목록·상세의 담당자 이름 표시용 배치 조회.
    List<MemberReference> findMemberReferences(List<Long> memberIds);

    // FR-AC-02 — 개인 액션 목록·상세의 소속팀 이름 표시용 배치 조회.
    List<TeamReference> findTeamReferences(List<Long> teamIds);

    // 2026-08-11 — 개인 액션 상세의 담당자 "역할" 라벨(예: "프론트엔드") 표시용 배치 조회.
    // DB 테이블명은 role(구 sub_team, V2.3.4로 개명) — team과 달리 리더가 없는 순수 분류
    // 태그다(이홍근 확인). member.role_id(구 sub_team_id, V2.3.2로 개명)는 null 허용(역할
    // 미지정)이라 호출측에서 null 필터링 후 넘긴다.
    List<SubTeamReference> findSubTeamReferences(List<Long> subTeamIds);

    // FR-AC-06 — 팀 액션 상세에 인라인으로 싣는 소속 프로젝트 첨부파일 목록. 단건 조회라 배치가 아니다.
    List<AttachmentReference> findProjectAttachments(Long projectId);

    // 2026-08-10 — 팀 액션 첨부파일 다운로드 URL 발급용 단건 조회. projectId를 함께 받아
    // 다른 프로젝트 소속 첨부파일 id를 넣어도 "없는 것"으로 답한다(존재 유출 방지).
    Optional<AttachmentReference> findProjectAttachmentById(Long attachmentId, Long projectId);

    // teamId는 OWNER 개설 회의면 null, relatedActionId는 팀 액션을 낳는 프로젝트 회의면 null이다.
    // title은 FR-AC-02 상세·목록의 "출처 회의" 표시용(2026-08-07 추가).
    // scheduledAt(meeting.start_at)은 FE 상세 화면의 "출처 회의 일시" 표시용(2026-08-11 추가).
    record MeetingReference(Long meetingId, Long teamId, Long relatedActionId, String title, LocalDateTime scheduledAt) {
    }

    // tag·name은 FR-AC-02 목록·상세의 프로젝트 표시용(2026-08-07 추가).
    record ProjectReference(Long projectId, LocalDate dueDate, String tag, String name) {
    }

    // subTeamId는 FR-AC-02 상세의 담당자 "역할" 라벨 조회용(2026-08-11 추가) — 역할 미지정이면 null.
    record MemberReference(Long memberId, String name, Long subTeamId) {
    }

    // leaderMemberId는 team.leader_member_id — 팀장 공석이면 null(정상 상태). 2026-08-11 —
    // TEAM 액션 상세의 "담당자" 표시(그 팀의 현재 팀장)에 재사용(이홍근 확인, 인수인계
    // 고아경보 기능이 쓰던 것과 같은 컬럼).
    record TeamReference(Long teamId, String name, Long leaderMemberId) {
    }

    // 2026-08-11 — member.role_id(구 sub_team_id)가 가리키는 "역할" 태그 이름. team과 별개
    // 테이블(role, 구 sub_team), 리더 없는 순수 분류용이라 TeamReference와 구분한다.
    record SubTeamReference(Long subTeamId, String name) {
    }

    // project 도메인 AttachmentResponse와 같은 shape이지만 presentation DTO를 직접 참조하지 않으므로
    // action이 자체 타입으로 복제해서 쓴다(0절 1항, TeamActionDetailResponse 주석 참고).
    record AttachmentReference(Long attachmentId, String fileName, String fileUrl, long fileSize, LocalDateTime createdAt) {
    }
}
