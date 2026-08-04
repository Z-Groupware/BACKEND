package com.module06.backend.action.infrastructure.persistence;

/* comment.
    identity(B, 윤종호) 소유 team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 팀 액션 목록·상세, 개인 액션 상세의 소속팀 표시에 팀명이 필요한데
    B의 엔티티를 직접 참조하면 0절 1항 위반이다. 조회할 컬럼은 id·name 정도다.
    팀 액션 목록(FR-AC-06)의 LEADER 스코프는 JWT claim의 teamId로 판단하고, 이 엔티티는
    화면 표시용 팀명 조회에만 쓰인다 — 권한 판단에 관여하지 않는다.

    연결된 클래스
    - ActionService · TeamActionService : 팀명 표시(GetActionDetailUseCase, GetTeamAction* 구현)
    - ActionJpaEntity                    : team_id 조인의 반대편
*/
public class TeamReferenceEntity {
}
