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
    identity(B, 윤종호) 소유 sub_team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    sub_team은 team(부서, 리더 1명)과 다른 개념이다 — "역할" 분류 태그일 뿐 리더가 없다
    (예: 개발팀 아래 프론트엔드/백엔드, 이홍근 확인·2026-08-11). member.sub_team_id로 조인되며
    역할 미지정 멤버는 null이라 존재하지 않을 수 있다. 쓰기 금지 — @Immutable.

    연결된 클래스
    - ActionService                    : 담당자 역할 라벨 표시(GetActionDetailUseCase 구현)
    - MemberReferenceEntity            : sub_team_id 조인의 반대편
    - ActionReferenceRepositoryAdapter : 배치 조회
*/
@Entity
@Table(name = "sub_team")
@Immutable
@Getter
@NoArgsConstructor
public class SubTeamReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "name")
    private String name;
}
