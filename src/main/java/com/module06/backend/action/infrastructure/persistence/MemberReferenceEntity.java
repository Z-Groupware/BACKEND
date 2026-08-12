package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.module06.backend.action.domain.repository.ActionReferenceRepository.ReferenceMemberStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    identity(B, 윤종호) 소유 member 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 개인 액션 상세·목록에서 담당자를 id만으로 보여줄 수 없고, B의 엔티티를
    직접 참조하면 0절 1항 위반이다. 수동 생성(FR-AC-01 예외 경로)의 assigneeMemberId
    회사 소속 검증에도 쓰인다. 2026-08-11 — 팀 대시보드 "팀원 현황"의 직급·재직상태 표시,
    오너 대시보드 "전체 사원"/"휴직자" 집계용으로 position_id·status·deleted_at을 추가한다.
    쓰기 금지. C는 권한 판단을 role 컬럼이 아니라 JWT authority로 하므로 이 엔티티와 권한 로직은 무관하다.

    status는 identity.member.domain.model.MemberStatus를 그대로 import하지 않고
    ActionReferenceRepository.ReferenceMemberStatus(action 자신의 domain 계약에 있는 로컬
    복제본)를 쓴다 — B 소유 도메인 모델 클래스를 C가 import하면 컴파일 의존이 생겨 0절 1항의
    취지(도메인 경계, id/컬럼 값만 공유)를 흐린다. ProjectReferenceEntity가 ProjectStatus를
    직접 import하는 것과 다른 이유는, 그건 같은 C 도메인 내부(다른 애그리게이트)라 소유자가
    같지만 여기는 소유자가 다른 B이기 때문이다. infrastructure가 자기 domain 계약에 의존하는
    것은 방향 규칙 위반이 아니다. 값 목록이 바뀌면(B가 enum에 값 추가) domain 쪽 복제본도
    같이 갱신해야 한다 — @Enumerated(STRING)이라 이름이 어긋나면 즉시 매핑 예외로 드러난다.

    연결된 클래스
    - ActionService     : 담당자 이름 표시(GetActionDetailUseCase 구현), 회사 소속 검증(수동 생성)
    - ActionJpaEntity   : assignee_member_id 조인의 반대편
*/
@Entity
@Table(name = "member")
@Immutable
@Getter
@NoArgsConstructor
public class MemberReferenceEntity {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name")
    private String name;

    // 2026-08-11 — 담당자 "역할" 라벨(예: "프론트엔드") 조회용. 실제 컬럼명은 role_id다 —
    // 원래 sub_team_id였으나 V2.3.2(rename_member_sub_team_id_to_role_id)로 이미 개명됨.
    // 리더 없는 순수 분류 태그라 team_id와는 다른 개념이다(이홍근 확인).
    @Column(name = "role_id")
    private Long subTeamId;

    // 2026-08-11 — 팀장이 팀원의 개인 액션 목록을 조회할 때 "같은 팀 소속인지" 스코프 확인용.
    // team_id는 V1부터 개명 이력 없음(마이그레이션 전수 확인, sub_team_id 사고 재발 방지).
    @Column(name = "team_id")
    private Long teamId;

    // 2026-08-11 — "직급" 라벨(PositionReferenceEntity) 조인용. V2.3.3에서 job_position_id →
    // position_id로 개명됨(identity.member.infrastructure.persistence.MemberJpaEntity 주석 확인).
    @Column(name = "position_id")
    private Long positionId;

    // 2026-08-11 — 오너 대시보드 "휴직자" 집계, 팀원 현황 "상태" 컬럼용.
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReferenceMemberStatus status;

    // 2026-08-11 — 소프트 삭제(RESIGNED) 필터링용. "전체 사원" 집계에서 제외하는 데 쓴다.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
