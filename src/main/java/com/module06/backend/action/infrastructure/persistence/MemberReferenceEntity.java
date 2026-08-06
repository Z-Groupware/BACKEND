package com.module06.backend.action.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    identity(B, 윤종호) 소유 member 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 개인 액션 상세·목록에서 담당자를 id만으로 보여줄 수 없고, B의 엔티티를
    직접 참조하면 0절 1항 위반이다. 수동 생성(FR-AC-01 예외 경로)의 assigneeMemberId
    회사 소속 검증에도 쓰인다.
    쓰기 금지. C는 권한 판단을 role 컬럼이 아니라 JWT authority로 하므로 이 엔티티와 권한 로직은 무관하다.

    @GeneratedValue를 붙이지 않는다 — identity(B)의 실쓰기 엔티티(MemberJpaEntity)도
    member.id를 @GeneratedValue 없이 애플리케이션이 채우는 방식이라(가입 흐름에서 별도 채번),
    여기서도 동일하게 맞춘다. 다른 참조 엔티티(ActionTeamReferenceEntity 등)가
    IDENTITY 전략을 명시한 것과는 이유가 다르다 — 그쪽은 실쓰기 엔티티가 IDENTITY라서 맞춘 것.

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
}
