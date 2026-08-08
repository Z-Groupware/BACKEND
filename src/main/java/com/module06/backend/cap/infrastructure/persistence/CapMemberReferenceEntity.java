package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    identity(윤종호) 소유 member 테이블을 읽기 전용으로 조회하는 참조 엔티티. action.MemberReferenceEntity와
    똑같은 목적(이름 조회)이지만 그대로 이름을 쓰면 @Entity 이름이 겹쳐 컨텍스트가 죽는다(팀이 이미 겪은
    사고 패턴) — 그래서 Cap 접두어로 분리한다. id에 @GeneratedValue를 안 붙이는 이유도 action과 동일:
    identity의 실쓰기 엔티티가 member.id를 애플리케이션이 채우는 방식이라 그에 맞춘다.

    쓰기 금지. 권한 판정에는 쓰지 않는다(cap은 role 컬럼이 아니라 JWT authority로 판정) — 이름 표시 전용.
*/
@Entity(name = "CapMemberReference")
@Table(name = "member")
@Immutable
@Getter
@NoArgsConstructor
public class CapMemberReferenceEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
