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
    identity(B, 윤종호) 소유 team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 팀 액션 목록·상세, 개인 액션 상세의 소속팀 표시에 팀명이 필요한데
    B의 엔티티를 직접 참조하면 0절 1항 위반이다. 쓰기 금지 — @Immutable로 dirty checking 자체를 막는다
    (project 도메인의 동명 참조 엔티티와 동일 패턴 — 단 Hibernate 엔티티명이 전역에서 겹치면
    안 돼서 클래스명은 Action 접두어로 구분했다).
    leaderMemberId는 findTeamActionsForDeparture(퇴사자 팀 액션 고아경보)용 — 이 팀의 팀장이
    누구인지로 "퇴사자가 팀장이었던 팀"을 가려낸다.
    팀 액션 목록(FR-AC-06)의 LEADER 스코프는 JWT claim의 teamId로 판단하고, 이 엔티티는
    화면 표시용 팀명 조회와 인수인계 조회에만 쓰인다 — 권한 판단에 관여하지 않는다.

    연결된 클래스
    - ActionService · TeamActionService : 팀명 표시(GetActionDetailUseCase, GetTeamAction* 구현)
    - ActionJpaEntity                    : team_id 조인의 반대편
    - SpringDataActionRepository         : findTeamActionsByLeaderMemberId에서 조인
*/
@Entity
@Table(name = "team")
@Immutable
@Getter
@NoArgsConstructor
public class ActionTeamReferenceEntity {

    // team 쓰기 엔티티(identity 모듈)와 동일한 IDENTITY 전략 — 안 맞추면 Hibernate가 같은
    // 테이블에 매핑된 두 엔티티의 식별자 생성 메타데이터를 혼동한다(2026-08-06 테스트로 확인).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name")
    private String name;

    @Column(name = "leader_member_id")
    private Long leaderMemberId;
}
