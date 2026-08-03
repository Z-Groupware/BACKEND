package com.module06.backend.project.infrastructure.persistence;

/* comment.
    organization(B, 윤종호) 소유 team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 타임라인·목록 응답에 부서명(teamName)이 필요한데, B의 엔티티를 직접
    참조하면 0절 1항 위반이다. 그래서 C가 자기 infrastructure에 읽기 전용 매핑을 따로 둔다.
    쓰기 금지 — insert·update·delete를 이 엔티티로 하지 않는다. 조회할 컬럼은 id·name뿐이다.
    부서 목록 드롭다운 같은 조회 API는 C가 만들지 않는다(B가 제공, FE가 직접 호출).

    연결된 클래스
    - GetProjectTimelineService : 부서명이 필요한 주 사용처
    - GetProjectListService     : 참여 부서 수·부서명 집계 시 사용
    - ProjectJpaEntity          : project_team 조인의 반대편
*/
public class TeamReferenceEntity {
}
