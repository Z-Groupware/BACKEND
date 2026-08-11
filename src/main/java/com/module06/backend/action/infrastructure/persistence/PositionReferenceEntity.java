package com.module06.backend.action.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    identity(B, 윤종호) 소유 position 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    identity.member.infrastructure.persistence.PositionRefEntity와 동일 패턴(같은 테이블,
    id·name만 조회) — 팀 대시보드 "팀원 현황" 테이블의 "직급" 컬럼 표시용으로 2026-08-11 추가.
    쓰기 금지.

    테이블명을 인용하지 않는다 — identity 쪽 원본 주석과 같은 이유(POSITION은 MySQL·H2
    양쪽에서 함수 이름이지만 예약어는 아니다, 인용하면 H2가 대소문자를 구분해 조회가 깨진다).

    @Id에 identity의 PositionJpaEntity·PositionRefEntity와 동일하게 IDENTITY 전략을 명시한다 —
    TeamReferenceEntity 주석과 같은 이유(같은 테이블에 매핑된 엔티티 간 식별자 생성 전략이
    갈리면 Hibernate 세션 캐시가 혼동한다, 2026-08-09 윤종호 확인).

    연결된 클래스
    - MemberReferenceEntity : position_id 조인의 반대편
*/
@Entity
@Table(name = "position")
@Immutable
@Getter
@NoArgsConstructor
public class PositionReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;
}
